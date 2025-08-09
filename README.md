# local-routing-gateway
A Spring Cloud Gateway project for ease Web app development in local

# How to use
1. Clone the project.
2. Update the application.properties with appropriate values of server auth credentials and details.
3. Then Update the first routing config with locally running service's contextPath.
4. In second routing config, just update the server's hostname.
5. Then build and run the project.
6. Now, use http://localhost:8764 in base url to route the api calls through this local-routing-gateway.

Note: For UI local projects, remoteUser will be passed from application.properties. For Backend local projects, comment the remoteUserHeader passage in AuthTokenGlobalFilter.java so that remoteUser passed from client code will be used.