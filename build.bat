@echo off
if not exist out mkdir out
javac -d out src\parking\*.java && echo Build OK
