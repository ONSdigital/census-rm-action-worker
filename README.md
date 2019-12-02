# census-rm-action-worker
[![Build Status](https://api.travis-ci.com/ONSdigital/census-rm-action-worker.svg?branch=master)](https://api.travis-ci.com/ONSdigital/census-rm-action-worker)

# Overview
TBC

#  Entrypoints / MessageEndpoints
TBC

# Testing

To test this service locally use:

```shell-script
mvn clean install
```   
This will run all of the unit tests, then if successful create a docker image for this application 
then bring up the required docker images from the test [docker compose YAML](src/test/resources/docker-compose.yml) (postgres, uacqidservice and rabbit)
to run the Integration Tests.

# Debug    
 If you want to debug the application/Integration tests start the required docker images by navigating 
 to [src/test/resources/](src/test/resources/) and then run :
 
```shell-script
docker-compose up
```

You should then be able to run the tests from your IDE.

# Configuration
By default the src/main/resources/application.yml is configured for 
[census-rm-docker-dev](https://github.com/ONSdigital/census-rm-docker-dev)

For production the configuration is overridden by the K8S apply script.

The queues are defined in test [definitions.json](src/test/resources/definitions.json) for Integration Tests.
