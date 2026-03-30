# Contributing

## API Documentation (Swagger / OpenAPI)

This project uses `springdoc-openapi` to generate interactive API documentation at `http://localhost:8080/swagger-ui.html`.

When adding or changing backend endpoints:

- Add Swagger annotations to controllers so endpoints stay documented and grouped in Swagger UI.
  - Use `@Tag` at the controller level (e.g., `Auth`, `Profiles`, `Matching`).
  - Use `@Operation` and (when helpful) `@ApiResponses` at the handler level.
- Add Swagger annotations to request/response models.
  - Use `@Schema` on DTO classes/fields for clear descriptions/examples.

Keeping these annotations up to date is required for new backend work.
