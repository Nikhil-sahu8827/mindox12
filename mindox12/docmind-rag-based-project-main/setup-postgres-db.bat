@echo off
echo ========================================================
echo Starting PostgreSQL 18 Service...
echo ========================================================
net start postgresql-x64-18

echo.
echo ========================================================
echo Checking / Creating 'docmind' Database in PostgreSQL...
echo ========================================================
set PGPASSWORD=root
"C:\Program Files\PostgreSQL\18\bin\createdb.exe" -h 127.0.0.1 -p 5432 -U postgres docmind 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Database 'docmind' created successfully!
) else (
    echo Database 'docmind' is ready or already exists.
)

echo.
echo ========================================================
echo Checking connection status...
echo ========================================================
"C:\Program Files\PostgreSQL\18\bin\pg_isready.exe" -h 127.0.0.1 -p 5432
echo ========================================================
pause
