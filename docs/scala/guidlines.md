# Package managment in scala

## Databases
 - all the database classes should be under the `main` package
 - all the models should be under the respective service package
 - for example, if the service is `users`, then the models should be under `users.models`

## services
 - all the service for example users must have their own package
 - for user service, the package should be `users`
 - any database related classes should be names as `UserDetailsRepository`
 - any other server hitting classes should be named as `UserService`
 - utils used by the service should be named as `UserUtils`
 - routes should be named as `UserRoutes`
 - all the controllers should be under the `controllers` package ex: `users.controllers.LoginController`

## Utils
 - all the utils should be under the `utils` package
 - these can be common classes used by multiple services or multiple projects

# Naming Conventions
 - all the classes should be named in `CamelCase`
 - all the methods should be named in `camelCase`
 - all the variables should be named in `camelCase`
 - constants should be named in `UPPER_CASE`
 - package names should be in `lowercase`
