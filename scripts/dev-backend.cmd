@echo off
rem Backend en local contra PostgreSQL + PostGIS (WSL, puerto 5433). Perfil dev con datos demo.
cd /d "C:\Users\quiqu\Documents\projects\rutaexpress\backend"
call "C:\Users\quiqu\Documents\projects\rutaexpress\backend\mvnw.cmd" -q spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:postgresql://localhost:5433/rutaexpress"
