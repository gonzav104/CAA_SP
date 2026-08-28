# Roadmap del Proyecto CAA_SP

## Fases Completadas
- [x] Inicialización del proyecto Spring Boot 4.1.1 (Java 21, Maven).
- [x] Configuración de `application.properties` para conexión a base de datos.
- [x] Despliegue de infraestructura local (PostgreSQL 15 vía Docker Compose).
- [x] Ejecución del script SQL inicial: Creación de tablas, tipos ENUM y restricciones CHECK.

## Fase Actual: Capa de Dominio (Entidades)
- [x] Crear paquete `com.caa.api.models`.
- [x] Mapear entidad `Usuario`.
- [x] Mapear entidad `Paciente`.
- [ ] Mapear entidades secundarias (Cartillas, Categorías, Items, Pictogramas).

## Fases Futuras
- [ ] Capa de Repositorios (Spring Data JPA).
- [ ] Capa de Servicios (Lógica de negocio).
- [ ] Capa de Controladores (Endpoints REST).