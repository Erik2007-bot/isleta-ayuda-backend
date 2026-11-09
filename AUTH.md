# Autenticación

Configurar estas variables de entorno antes de arrancar la aplicación:

- `JWT_SECRET`: clave aleatoria de al menos 32 bytes, codificada en Base64. Se utiliza para HS256 y no tiene valor predeterminado. Mantenerla fuera de Git y compartirla únicamente entre las instancias del backend.
- `FRONTEND_ORIGIN`: origen exacto de React, con esquema y puerto si corresponde, sin ruta ni barra final. Por ejemplo, `http://localhost:5173` en desarrollo. No se admiten comodines.

La conexión a MariaDB y `DB_PASSWORD` mantienen su configuración existente. Si faltan las variables de seguridad o son inválidas, el backend no arranca.

## Registro y login

`POST /api/auth/registro` recibe `nombre`, `email` y `password`. Asigna siempre `USER`, guarda BCrypt y devuelve los datos públicos con HTTP 201.

`POST /api/auth/login` recibe este JSON:

```json
{
  "email": "ana@example.invalid",
  "password": "la contraseña del usuario"
}
```

El email se normaliza igual que en el registro. La contraseña conserva sus espacios. Spring Security busca el usuario mediante JPA y comprueba el hash BCrypt. Un email inexistente o una contraseña incorrecta producen el mismo error HTTP 401.

La respuesta HTTP 200 contiene `token`, `tokenType` (`Bearer`), `expiresIn` (900 segundos) y `expiresAt` (instante UTC).

## Usuario autenticado

Enviar `GET /api/auth/me` con la cabecera `Authorization: Bearer <token>`.

Spring Security comprueba HS256, firma, caducidad, emisor, destinatario e identificador. Después se consulta el usuario actual en la base de datos y se devuelven únicamente `idUsuario`, `nombre`, `email` y `rol`. Si el usuario ya no existe, se devuelve HTTP 401.

Solo registro y login son públicos. El resto de las rutas continúa cerrado: sin autenticación devuelve 401; con un token válido, 403. CORS acepta únicamente el origen configurado y las solicitudes preflight necesarias. Las solicitudes de Android sin cabecera `Origin` utilizan la misma autenticación Bearer.

No se crean sesiones HTTP ni cookies de autenticación. CSRF está desactivado para esta API, que recibe los tokens exclusivamente por la cabecera Authorization. El cliente debe conservar el token en memoria y eliminarlo al cerrar sesión. No hay renovación ni revocación anticipada: el token caduca a los 15 minutos. Los roles incluidos en el token corresponden al momento del login.

## Pruebas

Ejecutar `.\mvnw.cmd test`. Los tests cargan explícitamente la configuración H2 y generan una clave aleatoria exclusiva de pruebas en memoria. También proporcionan un origen React exclusivo de pruebas. No necesitan las variables de entorno reales ni se conectan a MariaDB.
