"""Credential regressions with mocked PostgreSQL and Ollama; no live writes."""
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import types
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ("embed-memories.py", "search_memory.py")


class MemoryCredentialsTest(unittest.TestCase):
    def run_script(self, filename, environment):
        driver = types.ModuleType("psycopg")
        connection = mock.MagicMock()
        connection.__enter__.return_value = connection
        connection.cursor.return_value.__enter__.return_value.fetchall.return_value = []
        driver.connect = mock.Mock(return_value=connection)

        response = mock.MagicMock()
        response.__enter__.return_value = io.StringIO(
            json.dumps({"embeddings": [[0.0] * 768]})
        )
        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.dict("sys.modules", {"psycopg": driver}),
            mock.patch("urllib.request.urlopen", return_value=response) as request,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            try:
                runpy.run_path(str(ROOT / "memory-db" / filename))
            except SystemExit:
                driver.connect.assert_not_called()
                request.assert_not_called()
                raise
        return driver.connect.call_args

    def test_password_is_passed_literally_with_default_connection_settings(self):
        password = "synthetic 'quoted' \\ password with spaces"
        for filename in SCRIPTS:
            with self.subTest(script=filename):
                call = self.run_script(filename, {"POSTGRES_PASSWORD": password})
                self.assertEqual(call.args, ())
                self.assertEqual(call.kwargs, {
                    "dbname": "ai_memory",
                    "user": "ai_memory",
                    "password": password,
                    "host": "127.0.0.1",
                    "port": "5432",
                })

    def test_environment_overrides_connection_settings(self):
        environment = {
            "POSTGRES_PASSWORD": "synthetic-password",
            "POSTGRES_DB": "test_memory",
            "POSTGRES_USER": "test_user",
            "POSTGRES_HOST": "test-db",
            "POSTGRES_PORT": "15432",
        }
        for filename in SCRIPTS:
            with self.subTest(script=filename):
                call = self.run_script(filename, environment)
                self.assertEqual(call.kwargs, {
                    "dbname": "test_memory",
                    "user": "test_user",
                    "password": "synthetic-password",
                    "host": "test-db",
                    "port": "15432",
                })

    def test_missing_or_empty_password_stops_before_network_access(self):
        for filename in SCRIPTS:
            for environment in ({}, {"POSTGRES_PASSWORD": ""}):
                with self.subTest(script=filename, empty=bool(environment)):
                    with self.assertRaisesRegex(SystemExit, "POSTGRES_PASSWORD is required"):
                        self.run_script(filename, environment)


if __name__ == "__main__":
    unittest.main()
