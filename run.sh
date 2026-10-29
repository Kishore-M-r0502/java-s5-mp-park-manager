#!/bin/sh
# ./run.sh          -> GUI
# ./run.sh console  -> console version
cd "$(dirname "$0")"
MAIN=parking.ParkingApp
[ "$1" = "console" ] && MAIN=parking.Main
exec java -cp "out;lib/*" "$MAIN"
