@echo off
echo ============================================
echo  Инициализация Git для WinStudio Pro
echo ============================================

cd /d "C:\WinStudioPro_Portable_Project\WinStudioPro"

git init
git config user.name "WinStudio Developer"
git config user.email "dev@winstudiopro.local"

echo.
echo Добавляем файлы...
git add app\src\main\java\ru\winstudiopro\app\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\data\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\domain\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\domain\calculator\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\domain\model\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\pdf\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\ui\*.kt 2>nul
git add app\src\main\java\ru\winstudiopro\app\ui\cad\*.kt 2>nul
git add app\src\main\AndroidManifest.xml 2>nul
git add app\build.gradle.kts 2>nul
git add build.gradle.kts 2>nul
git add settings.gradle.kts 2>nul
git add gradle.properties 2>nul

git commit -m "v1.0 — рабочая версия: CAD-редактор, PDF-отчёты, раскрой профилей, система Asas"

echo.
echo ============================================
echo  Готово! Git инициализирован.
echo  Текущая версия сохранена как v1.0
echo ============================================
pause
