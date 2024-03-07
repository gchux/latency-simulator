# Maven

-   in `pom.xml`, set the following `<properties>`:
    - `gcp.projectId`  
    - `gcp.artifacts.base`  
    - `gcp.artifacts.repo`  
    - `docker.image.name`  
    - `docker.image.tag`  

-   `JIB` references:
    - https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin
    - https://cloud.google.com/java/getting-started/jib


## Build 

```shell
./mvnw clean package jib:dockerBuild
```

## Build and push 

```shell
./mvnw clean package jib:build
```

# Manually

## build and push

```shell
# set this variables 1st
export GCP_PROJECT_ID='...'
export GCP_ARTIFACTS_BASE='...'
export GCP_ARTIFACTS_REPO='...'
export DOCKER_IMAGE_NAME='...'
export DOCKER_IMAGE_TAG='...'
export DOCKER_IMAGE="${GCP_ARTIFACTS_BASE}/${GCP_PROJECT_ID}/${GCP_ARTIFACTS_REPO}/${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
./mvnw clean package
docker build -t ${DOCKER_IMAGE} .
docker push ${DOCKER_IMAGE}
```
