@echo off
title 0Media — Servidor Local en Tiempo Real
echo ===================================================================
echo     0Media Digital Signage — Servidor Local de Comunicacion
echo ===================================================================
echo Iniciando servidor backend en puerto 3000...
echo Presiona Ctrl+C para detener el servidor.
echo.
cd /d "%~dp0backend"
node servidor-local.js
pause
