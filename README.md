# Property Tax Management (demo)

Portfolio demo of a municipal property tax system modernized from a legacy Oracle Forms/PL/SQL style
into Spring Boot + React on AWS. **All data and tax rates are fictional.** Not affiliated with any municipality.

## Run the backend
Requires Java 21+.
```bash
cd backend
./mvnw spring-boot:run
curl localhost:8080/actuator/health   # {"status":"UP"}
```

See [docs/](docs/) for requirements, architecture, and standards.
