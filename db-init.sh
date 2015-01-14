echo
echo Importing Picos..
echo
mongoimport --db welcomerFramework --collection picos --file picos.db.example.json

echo
echo Importing ECIs..
echo
mongoimport --db welcomerFramework --collection ecis --file ecis.db.example.json

echo
echo Done
echo
