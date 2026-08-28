# Contexto del Proyecto: API Backend CAA

## Descripción General
API RESTful desarrollada en Java y Spring Boot para un sistema de Comunicación Aumentativa y Alternativa (CAA). El sistema permite a terapeutas y familiares gestionar pacientes y crear tableros/cartillas de pictogramas personalizados.

## Stack Tecnológico
- **Lenguaje:** Java 21 (o superior)
- **Framework:** Spring Boot 4.1.1
- **Persistencia:** Spring Data JPA / Hibernate
- **Base de Datos:** PostgreSQL
- **Herramientas:** Lombok (obligatorio para getters, setters, constructores)

## Reglas de Arquitectura y Código
1. **Patrón de Diseño:** MVC estructurado en capas (Controllers, Services, Repositories, Models/Entities).
2. **Tipos de Datos Clave:**
    - Todas las Claves Primarias (IDs) en la base de datos son de tipo `UUID`. En Java deben mapearse como `java.util.UUID`.
    - Las fechas deben usar `java.time.LocalDateTime` o `java.time.LocalDate`.
3. **Mapeo Objeto-Relacional (JPA):**
    - Usar `snake_case` para nombres de tablas y columnas en la BD (ej: `pacientes_familiares`).
    - Usar `camelCase` para las propiedades en Java.
    - Definir explícitamente el nombre de la tabla con `@Table(name = "nombre_tabla")`.
4. **Lombok:** Reducir código boilerplate usando `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` y `@Builder` donde corresponda.