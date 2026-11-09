@echo off
rem Uso: dev-frontend.cmd admin 4200 / dev-frontend.cmd driver 4201   (BACKEND_URL por defecto http://localhost:8080)
if "%BACKEND_URL%"=="" set BACKEND_URL=http://localhost:8080
cd /d "C:\Users\quiqu\Documents\projects\rutaexpress\frontend"
call npx ng serve %1 --port %2
