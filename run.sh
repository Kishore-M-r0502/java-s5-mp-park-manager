#!/bin/sh
# ./run.sh          -> GUI
# ./run.sh console  -> console version
cd "$(dirname "$0")"
MAIN=parking.ParkingApp
[ "$1" = "console" ] && MAIN=parking.Main
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=";" ;;
  *) SEP=":" ;;
esac
exec java -cp "out${SEP}lib/*" "$MAIN"
