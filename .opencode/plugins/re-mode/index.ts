import { Plugin } from "@opencode/plugin"
import { install } from "./router.ts"

export default Plugin.define({ id: "local.re-mode", setup: install })
