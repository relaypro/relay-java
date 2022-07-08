# Copyright © 2022 Relay Inc.

run:
	./gradlew app:run

build: 
	./gradlew build

# builds sdk jar in sdk/build/libs/sdk.jar
jar:
	./gradlew sdk:jar
	
clean:
	./gradlew clean

docs:
	./gradlew javadoc
