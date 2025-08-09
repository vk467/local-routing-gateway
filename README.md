# local-routing-gateway
A Spring Cloud Gateway project for ease Web app development in local

# How to use Local Routing gateway with one local project
1. Clone the project.
2. Update the application.properties with appropriate values of server auth credentials and details.
3. Then Update the first routing config(routeId=local) with locally running service's contextPath.
4. In second routing config(routeId=server), just update the server's hostname.
5. Then build and run the project.
6. Now, use http://localhost:8764 in base url to route the api calls through this local-routing-gateway.

***Note:*** For UI local projects, remoteUser will be passed from *application.properties*. For Backend local projects, comment the remoteUserHeader passage in *AuthTokenGlobalFilter.java* so that remoteUser passed from client code will be used.


## Configure Gateway to Route Multiple local projects
Update the *application.properties* as below to use two local projects(local-service-1 and local-service-2) in Gateway.
```
spring.cloud.gateway.routes[0].id=local
spring.cloud.gateway.routes[0].uri=http://localhost:8080
spring.cloud.gateway.routes[0].predicates[0]=Path=/<<local-service-1>>/**

spring.cloud.gateway.routes[1].id=local-route-2
spring.cloud.gateway.routes[1].uri=http://localhost:8081
spring.cloud.gateway.routes[1].predicates[0]=Path=/<<local-service-2>>/**

spring.cloud.gateway.routes[2].id=server
spring.cloud.gateway.routes[2].uri=<<serverHostname:port>>
spring.cloud.gateway.routes[2].filters[0]=RewritePath=/(?<segment>.*), ${context.path}/${segment}
spring.cloud.gateway.routes[2].predicates[0]=Path=/**
```
1. Update the localhost port numbers accordingly in **uri** property.
2. Replace the placeholders <<local-service-#>> with the appropriate context paths for each local project in **predicates[0]=Path** property.
3. In above way, you can route multiple local projects through gateway.

***Note: Server route should be at the end. Local routes should be added prior to the server route.***
