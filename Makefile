
run:
	./gradlew app:run

build: 
	./gradlew build

# builds sdk jar in sdk/build/libs/sdk.jsr
jar:
	./gradlew sdk:jar
	
clean:
	./gradlew clean

docs:
	./gradlew javadoc

