@echo off
set MAIN=parking.ParkingApp
if "%1"=="console" set MAIN=parking.Main
java -cp "out;lib/*" %MAIN%
