package dev.joe.aimemoryservice;

import dev.joe.aimemoryservice.controller.ProjectController;
import dev.joe.aimemoryservice.dto.CreateProjectRequest;
import dev.joe.aimemoryservice.dto.ProjectResponse;
import dev.joe.aimemoryservice.exceptions.GlobalExceptionHandler;
import dev.joe.aimemoryservice.exceptions.ProjectAlreadyExistsException;
import dev.joe.aimemoryservice.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import(GlobalExceptionHandler.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void createsProject() throws Exception {
        ProjectResponse response = projectResponse();

        when(projectService.createProject(any(CreateProjectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "local-ai-stack",
                                  "description": "Local AI infrastructure"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("local-ai-stack"))
                .andExpect(jsonPath("$.description")
                        .value("Local AI infrastructure"));
    }

    @Test
    void listsProjects() throws Exception {
        when(projectService.getProjects())
                .thenReturn(List.of(projectResponse()));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("local-ai-stack"));
    }

    @Test
    void rejectsBlankProjectName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Request validation failed"));
    }

    @Test
    void reportsDuplicateProject() throws Exception {
        when(projectService.createProject(any(CreateProjectRequest.class)))
                .thenThrow(new ProjectAlreadyExistsException("local-ai-stack"));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "local-ai-stack"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Project already exists"))
                .andExpect(jsonPath("$.status").value(409));
    }

    private static ProjectResponse projectResponse() {
        OffsetDateTime timestamp =
                OffsetDateTime.parse("2026-10-03T14:00:00Z");

        return new ProjectResponse(
                1,
                "local-ai-stack",
                "Local AI infrastructure",
                timestamp,
                timestamp
        );
    }
}