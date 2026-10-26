# Sistema de Biblioteca - Microservicios con Monitoreo en Kubernetes

Sistema de microservicios para la gestión de préstamos y catálogo de libros desarrollado con **Java 17**, **Spring Boot 3.3.5**, instrumentado con **Micrometer & Prometheus** y desplegado sobre un clúster local de **Kubernetes (Minikube con perfil dedicado `biblioteca`)** con observabilidad y monitoreo integral mediante **kube-prometheus-stack (Prometheus, Grafana y Alertmanager)**.

---

## 📋 Tabla de Contenidos
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
- [Estructura del Repositorio](#-estructura-del-repositorio)
- [Tecnologías Utilizadas](#-tecnologías-utilizadas)
- [Instalación de Herramientas en Windows](#-instalación-de-herramientas-en-windows)
- [Puesta en Marcha](#-puesta-en-marcha)
  - [Opción 1: Automatizada (Recomendada)](#opción-1-puesta-en-marcha-automatizada-con-script)
  - [Opción 2: Paso a Paso Manual](#opción-2-puesta-en-marcha-manual)
- [URLs de Acceso](#-urls-de-acceso)
- [Tabla de Endpoints de la API](#-tabla-de-endpoints-de-la-api)
- [Tabla de Métricas Micrometer](#-tabla-de-métricas-micrometer)
- [Tabla de Reglas de Alerta](#-tabla-de-reglas-de-alerta)
- [Las 3 Casuísticas de Monitoreo](#-las-3-casuísticas-de-monitoreo)
- [Pruebas de Carga y Tráfico](#-pruebas-de-carga-y-tráfico)
- [Lecciones Aprendidas y Problemas Conocidos](#-lecciones-aprendidas-y-problemas-conocidos)
- [Limpieza del Entorno](#-limpieza-del-entorno)

---

## 🏛 Arquitectura del Sistema

El dominio modela el ciclo de vida de préstamos bibliotecarios mediante dos microservicios desacoplados:

1. **`libros-service` (Servicio A - Puerto 8081)**:
   - Administra el catálogo bibliográfico (novelas, clásicos, ciencias, divulgación, ingeniería).
   - Controla el inventario de copias físicas disponibles y procesa reservas/devoluciones de copias atómicamente.
   - Soporta simulación de latencia artificial mediante la variable de entorno `LIBROS_DELAY_MS`.
2. **`prestamos-service` (Servicio B - Puerto 8082)**:
   - Gestiona el registro de préstamos de libros solicitados por socios de la biblioteca.
   - Consulta y descuenta copias disponibles en `libros-service` vía HTTP usando **Spring WebClient** reactivo con timeout configurado (`LIBROS_TIMEOUT_MS`, por defecto 2000 ms).
   - Si `libros-service` supera el timeout o experimenta caídas, maneja el fallo de manera controlada devolviendo `HTTP 503 Service Unavailable`, registra el error en bitácora e incrementa los contadores de fallas en Micrometer.

```
                           +---------------------------+
                           |     Prometheus Scrape     |
                           +-------------+-------------+
                                         |
                       +-----------------+-----------------+
                       |                                   |
                       v                                   v
             +--------------------+              +--------------------+
             | prestamos-service  |              |   libros-service   |
  Socio ---->|    (Puerto 8082)   |---WebClient->|    (Puerto 8081)   |
             |                    |   (Timeout)  |                    |
             +--------------------+              +--------------------+
```

---

## 📁 Estructura del Repositorio

El proyecto mantiene una estructura limpia, desacoplada y orientada a GitOps:

```text
biblioteca-monitoreo/
├── docs/
│   ├── informe.md                # Informe técnico completo (Partes A, B, C y D)
│   ├── GUIA_CAPTURAS.md          # Guía detallada y probada paso a paso para capturas
│   ├── capturas/                 # Capturas de pantalla de evidencias y casuísticas
│   └── evidencias/               # Salidas reales de comandos de validación (.txt)
├── k8s/
│   ├── app/
│   │   ├── namespace.yaml        # Namespace aislado de aplicación (biblioteca)
│   │   ├── libros.yaml           # Deployment (Recreate), Service NodePort 30081 y Probes
│   │   └── prestamos.yaml        # Deployment, Service NodePort 30082 y Probes
│   └── monitoring/
│       ├── values.yaml           # Configuración personalizada de kube-prometheus-stack
│       ├── servicemonitors.yaml  # ServiceMonitors para libros y prestamos
│       ├── alertas.yaml          # PrometheusRules (5 alertas de disponibilidad y negocio)
│       └── dashboard.json        # Dashboard de Grafana con 7 paneles exportado
├── loadtesting/
│   └── stress.sh                 # Script de generación de tráfico de préstamos parametrizable
├── microservices/
│   ├── libros-service/           # Código Java 17, Spring Boot 3.3.5, Maven y Dockerfile multi-stage
│   └── prestamos-service/        # Código Java 17, Spring Boot 3.3.5, Maven y Dockerfile multi-stage
├── scripts/
│   ├── setup.sh                  # Automatización total de despliegue desde cero
│   └── teardown.sh               # Limpieza y desmontaje de recursos (soporta --cluster)
├── .gitattributes                # Enforce saltos de línea LF para scripts bash
├── .gitignore                    # Exclusión de artefactos compilados y temporales
└── README.md                     # Documentación principal del proyecto
```

---

## 🛠 Tecnologías Utilizadas

- **Lenguaje**: Java 17 (Eclipse Temurin)
- **Framework**: Spring Boot 3.3.5 (Spring MVC, Spring WebFlux / WebClient reactivo, Spring Boot Actuator)
- **Instrumentación y Métricas**: Micrometer con registro Prometheus (`/metrics`)
- **Contenedorización**: Docker con construcción multi-etapa (*multi-stage build*) usando `maven:3.9-eclipse-temurin-17` y `eclipse-temurin:17-jre-alpine`
- **Orquestación**: Kubernetes v1.34.0 (Minikube con driver y container runtime Docker en perfil `biblioteca`)
- **Gestor de Paquetes K8s**: Helm v3 / v4
- **Stack de Observabilidad**: `kube-prometheus-stack` (Prometheus Operator, Grafana, Alertmanager)

---

## 💻 Instalación de Herramientas en Windows

Se recomienda utilizar **winget** desde una consola PowerShell con privilegios de Administrador:

```powershell
# 1. Java Development Kit (JDK 17)
winget install Oracle.JDK.17

# 2. Apache Maven
winget install Apache.Maven

# 3. Docker Desktop (activar motor WSL2 o Hyper-V)
winget install Docker.DockerDesktop

# 4. Minikube
winget install Kubernetes.minikube

# 5. kubectl
winget install Kubernetes.kubectl

# 6. Helm
winget install Helm.Helm

# 7. Git (incluye Git Bash para ejecutar scripts .sh)
winget install Git.Git
```

### Verificación de Versiones Instaladas:

```powershell
java -version
mvn -version
docker --version
minikube version
kubectl version --client
helm version
& "C:\Program Files\Git\bin\bash.exe" --version
```

---

## 🚀 Puesta en Marcha

### Opción 1: Puesta en Marcha Automatizada con Script

El script `scripts/setup.sh` levanta el clúster Minikube en el perfil `biblioteca`, instala el stack de monitoreo, compila las imágenes de Docker, despliega los servicios, configura los ServiceMonitors, aprovisiona el dashboard de Grafana y establece los port-forwards locales:

```bash
# Abrir Git Bash o terminal en la raíz del proyecto
cd tienda-biblioteca-monitoreo

# Ejecutar el aprovisionamiento
bash scripts/setup.sh
```

### Opción 2: Puesta en Marcha Manual

Si prefieres realizar el despliegue paso a paso:

```bash
# 1. Iniciar Minikube en el perfil dedicado 'biblioteca'
minikube -p biblioteca start --driver=docker --container-runtime=docker --cpus=4 --memory=5120 --kubernetes-version=v1.34.0
minikube -p biblioteca addons enable metrics-server
kubectl config use-context biblioteca

# 2. Instalar el stack de monitoreo con Helm
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack -n monitoring -f k8s/monitoring/values.yaml --wait

# 3. Configurar entorno Docker de Minikube y compilar imágenes
eval $(minikube -p biblioteca docker-env --shell bash)
docker build -t libros-service:1.0.0 microservices/libros-service
docker build -t prestamos-service:1.0.0 microservices/prestamos-service

# 4. Desplegar microservicios de la biblioteca
kubectl apply -f k8s/app/namespace.yaml
kubectl apply -f k8s/app/libros.yaml
kubectl apply -f k8s/app/prestamos.yaml
kubectl rollout status deployment/libros-service -n biblioteca
kubectl rollout status deployment/prestamos-service -n biblioteca

# 5. Desplegar monitoreo de aplicaciones y dashboard
kubectl apply -f k8s/monitoring/servicemonitors.yaml
kubectl apply -f k8s/monitoring/alertas.yaml
kubectl create configmap biblioteca-dashboard -n monitoring --from-file=biblioteca-dashboard.json=k8s/monitoring/dashboard.json --dry-run=client -o yaml | kubectl apply -f -
kubectl label configmap biblioteca-dashboard -n monitoring grafana_dashboard=1 --overwrite

# 6. Recargar Prometheus para absorber ServiceMonitors y reglas
kubectl rollout restart statefulset/prometheus-monitoring-kube-prometheus-prometheus -n monitoring
kubectl rollout status statefulset/prometheus-monitoring-kube-prometheus-prometheus -n monitoring

# 7. Abrir port-forwards
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80 &
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090 &
kubectl port-forward -n biblioteca svc/libros-service 8081:8081 &
kubectl port-forward -n biblioteca svc/prestamos-service 8082:8082 &
```

---

## 🌐 URLs de Acceso

Con los port-forwards activos, todos los servicios se acceden directamente en `localhost`:

| Componente | URL Local | Credenciales / Detalles |
| :--- | :--- | :--- |
| **Grafana** | `http://localhost:3000` | Usuario: `admin` \| Contraseña: `Biblioteca2026!` (ver `k8s/monitoring/values.yaml`) |
| **Prometheus** | `http://localhost:9090` | Targets: `http://localhost:9090/targets`<br>Alertas: `http://localhost:9090/alerts` |
| **libros-service** | `http://localhost:8081` | Salud: `/health` \| Métricas: `/metrics` \| Libros: `/libros` |
| **prestamos-service**| `http://localhost:8082` | Salud: `/health` \| Métricas: `/metrics` \| Préstamos: `/prestamos` |

> **Nota para acceso directo vía NodePort:**
> - Minikube IP: `minikube -p biblioteca ip`
> - Grafana NodePort: `30300` (`http://$(minikube -p biblioteca ip):30300`)
> - Prometheus NodePort: `30900` (`http://$(minikube -p biblioteca ip):30900`)
> - Libros NodePort: `30081` (`http://$(minikube -p biblioteca ip):30081`)
> - Préstamos NodePort: `30082` (`http://$(minikube -p biblioteca ip):30082`)

---

## 📡 Tabla de Endpoints de la API

### Servicio A: `libros-service` (Puerto 8081)

| Método | Endpoint | Descripción | Códigos HTTP |
| :--- | :--- | :--- | :--- |
| `GET` | `/health` | Chequeo de salud ultrarrápido e independiente | `200 OK` |
| `GET` | `/metrics` | Métricas Micrometer expuestas en formato estándar Prometheus | `200 OK` |
| `GET` | `/libros` | Catálogo de libros. Soporta filtro opcional `?categoria=CIENCIAS` | `200 OK` |
| `GET` | `/libros/{id}` | Consulta detallada de un libro por su ID (ej. `L003`) | `200 OK`, `404 Not Found` |
| `GET` | `/libros/sin-copias` | Libros cuyo stock de copias físicas disponibles llegó a 0 | `200 OK` |
| `POST` | `/libros` | Registra o actualiza un libro en el catálogo | `201 Created` |
| `POST` | `/libros/{id}/prestar` | Descuenta atómicamente 1 copia para préstamo | `200 OK`, `404 Not Found`, `409 Conflict` (sin copias) |
| `POST` | `/libros/{id}/devolver` | Incrementa atómicamente 1 copia por devolución | `200 OK`, `404 Not Found` |

### Servicio B: `prestamos-service` (Puerto 8082)

| Método | Endpoint | Descripción | Códigos HTTP |
| :--- | :--- | :--- | :--- |
| `GET` | `/health` | Chequeo de salud del servicio de préstamos | `200 OK` |
| `GET` | `/metrics` | Métricas Micrometer de préstamos para Prometheus | `200 OK` |
| `POST` | `/prestamos` | Crea un préstamo validando y descontando copia en `libros-service` | `201 Created`, `400 Bad Request`, `404 Not Found`, `409 Conflict`, `503 Service Unavailable` |
| `GET` | `/prestamos` | Listado histórico de préstamos registrados | `200 OK` |
| `GET` | `/prestamos/{id}` | Consulta de un préstamo específico por su código | `200 OK`, `404 Not Found` |
| `GET` | `/prestamos/resumen` | Resumen de totales: préstamos emitidos, activos y devueltos | `200 OK` |
| `POST` | `/prestamos/{id}/devolver` | Marca el préstamo como `DEVUELTO` y repone la copia en `libros-service` | `200 OK`, `404 Not Found` |
| `GET` | `/biblioteca/libros` | Consulta delegada del catálogo completo a través de préstamos | `200 OK`, `503 Service Unavailable` |

---

## 📊 Tabla de Métricas Micrometer

| Servicio | Nombre en Java (Micrometer) | Nombre en Prometheus | Tipo | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `libros-service` | `libros.consultas` | `libros_consultas_total` | **Counter** | Total acumulado de consultas al catálogo |
| `libros-service` | `libros.prestamos.exitosos` | `libros_prestamos_exitosos_total` | **Counter** | Total de copias prestadas satisfactoriamente |
| `libros-service` | `libros.prestamos.rechazados` | `libros_prestamos_rechazados_total` | **Counter** | Solicitudes rechazadas por copias agotadas |
| `libros-service` | `libros.titulos` | `libros_titulos` | **Gauge** | Cantidad de títulos distintos en catálogo |
| `libros-service` | `libros.copias.disponibles` | `libros_copias_disponibles` | **Gauge** | Total de copias físicas disponibles para préstamo |
| `libros-service` | `libros.delay.artificial.ms` | `libros_delay_artificial_ms` | **Gauge** | Retardo artificial inyectado (ms) |
| `libros-service` | `libros.consulta.duracion` | `libros_consulta_duracion_seconds` | **Timer** | Duración de las operaciones en catálogo (con histograma) |
| `prestamos-service` | `prestamos.confirmados` | `prestamos_confirmados_total` | **Counter** | Total de préstamos emitidos a socios |
| `prestamos-service` | `prestamos.libros.errores` | `prestamos_libros_errores_total` | **Counter** | Fallas o timeouts al contactar `libros-service` |
| `prestamos-service` | `prestamos.rechazados` | `prestamos_rechazados_total` | **Counter** | Préstamos rechazados (con etiqueta `motivo`) |
| `prestamos-service` | `prestamos.activos` | `prestamos_activos` | **Gauge** | Préstamos vigentes aún no devueltos |
| `prestamos-service` | `prestamos.libros.timeout.ms` | `prestamos_libros_timeout_ms` | **Gauge** | Límite de timeout configurado hacia libros (ms) |
| `prestamos-service` | `prestamos.libros.llamada.duracion` | `prestamos_libros_llamada_duracion_seconds` | **Timer** | Latencia de llamadas WebClient a libros (con histograma) |

---

## 🔔 Tabla de Reglas de Alerta

Definidas en [`k8s/monitoring/alertas.yaml`](k8s/monitoring/alertas.yaml) dentro del recurso `PrometheusRule`:

| Nombre de Alerta | Severidad | Expresión PromQL | Duración (`for`) | Resumen / Significado |
| :--- | :--- | :--- | :--- | :--- |
| `LibrosServiceCaido` | `critical` | `up{job="libros-service", namespace="biblioteca"} == 0 or absent(up{job="libros-service", namespace="biblioteca"})` | 30s | `libros-service` está fuera de servicio. No se pueden procesar préstamos. |
| `PrestamosServiceCaido` | `critical` | `up{job="prestamos-service", namespace="biblioteca"} == 0 or absent(up{job="prestamos-service", namespace="biblioteca"})` | 30s | `prestamos-service` no responde. Los socios no pueden operar. |
| `LibrosLentoOConTimeouts` | `warning` | `increase(prestamos_libros_errores_total[1m]) > 2` | 30s | Llamadas a libros superan el timeout de 2000 ms. Préstamos perdiéndose. |
| `CopiasTotalesBajas` | `warning` | `libros_copias_disponibles < 20` | 1m | Quedan menos de 20 copias en todo el catálogo de la biblioteca. |
| `PrestamosRechazadosSinCopias` | `warning` | `increase(libros_prestamos_rechazados_total[2m]) > 3` | 30s | Más de 3 préstamos rechazados por copias agotadas en 2 minutos. |

---

## 🧪 Las 3 Casuísticas de Monitoreo

### 🔴 Casuística 1: El Servicio Caído (CrashLoopBackOff)
- **Contexto**: Un error de configuración inyecta un límite de memoria inviable para la JVM (`JAVA_TOOL_OPTIONS=-Xmx1m`), provocando que el contenedor de `libros-service` no pueda arrancar y falle de inmediato en un bucle continuo de reinicios (`CrashLoopBackOff`).
- **Síntoma**: El Pod entra en fallo cíclico, el Liveness Probe no responde y el target de Prometheus pasa a `DOWN`.
- **Activación**:
  ```bash
  kubectl set env deployment/libros-service -n biblioteca JAVA_TOOL_OPTIONS=-Xmx1m
  ```
- **Diagnóstico**:
  - `kubectl get pods -n biblioteca`: Pod en `CrashLoopBackOff`.
  - Prometheus `/targets`: `libros-service` en estado **DOWN (0/1 up)**.
  - Prometheus `/alerts`: `LibrosServiceCaido` en estado **FIRING**.
  - Grafana: Panel "Estado de los servicios" muestra `libros-service` en rojo (`DOWN`).
- **Resolución**:
  ```bash
  kubectl set env deployment/libros-service -n biblioteca JAVA_TOOL_OPTIONS-
  kubectl rollout status deployment/libros-service -n biblioteca
  ```
- **Lección técnica**: Los probes de salud y la estrategia de despliegue (`Recreate`) garantizan que Kubernetes reconozca los fallos fatales de la JVM, mientras que Prometheus dispara alertas de disponibilidad crítica antes de que los usuarios reporten la caída.

---

### 🟡 Casuística 2: El Servicio Lento (Propagación de Latencia y Timeouts)
- **Contexto**: `libros-service` experimenta lentitud artificial (`LIBROS_DELAY_MS=3000`), superando el timeout de 2000 ms configurado en el WebClient de `prestamos-service`.
- **Síntoma**: Ambos microservicios siguen con pods en `Running` y targets en `UP`, pero las peticiones de préstamo fallan con `HTTP 503 (El servicio de libros no responde)`.
- **Activación**:
  ```bash
  # 1. Configurar delay de 3 segundos en libros-service
  kubectl set env deployment/libros-service -n biblioteca LIBROS_DELAY_MS=3000
  kubectl rollout status deployment/libros-service -n biblioteca

  # 2. Generar tráfico de solicitudes con stress.sh
  bash loadtesting/stress.sh -n 30 -d 0.5
  ```
- **Diagnóstico**:
  - `stress.sh` reporta fallos `HTTP 503  LIBROS NO RESPONDE`.
  - En Grafana, `libros_delay_artificial_ms` marca 3000 ms frente a 2000 ms de timeout.
  - El contador `prestamos_libros_errores_total` se dispara.
  - La alerta `LibrosLentoOConTimeouts` pasa a estado **FIRING**.
- **Resolución**:
  ```bash
  kubectl set env deployment/libros-service -n biblioteca LIBROS_DELAY_MS=0
  kubectl rollout status deployment/libros-service -n biblioteca
  ```
- **Lección técnica**: En microservicios la latencia es silenciosa y se propaga. Sin métricas de Gauges (delay vs timeout) y Timers, los equipos culparían erróneamente a `prestamos-service`, cuando el origen real es la degradación en `libros-service`.

---

### 🟠 Casuística 3: Agotamiento de Copias en Libro Estrella en Época de Exámenes
- **Contexto**: En vísperas de exámenes parciales universitarios, una ola masiva de estudiantes solicita el libro de mayor demanda: `L003 - Cálculo de una variable (James Stewart)`, el cual cuenta únicamente con 3 copias físicas disponibles.
- **Síntoma**: Los primeros 3 préstamos se confirman exitosamente (`HTTP 201 Created`). A partir del 4º intento, los estudiantes reciben rechazo inmediato con `HTTP 409 Conflict ("No hay copias disponibles")`.
- **Activación**:
  ```bash
  bash loadtesting/stress.sh -n 12 -p L003 -d 0.5 -c "estudiante-examenes"
  ```
- **Diagnóstico**:
  - `stress.sh` muestra 3 respuestas `HTTP 201 (CONFIRMADO)` y 9 respuestas `HTTP 409 (SIN COPIAS)`.
  - El contador `libros_prestamos_rechazados_total` asciende a 9.
  - Las copias disponibles de L003 bajan a 0.
  - La alerta de negocio `PrestamosRechazadosSinCopias` pasa a estado **FIRING**.
- **Resolución**:
  ```bash
  # Reiniciar el pod para recargar el dataset inicial en memoria (o reponer vía API POST /libros)
  kubectl rollout restart deployment/libros-service -n biblioteca
  kubectl rollout status deployment/libros-service -n biblioteca
  ```
- **Lección técnica**: Las métricas de infraestructura (CPU, RAM, HTTP 500) se encontraban al 100% saludables. Sin embargo, la biblioteca falló en su misión de servicio por quiebre de stock. Monitorear métricas de negocio en tiempo real permite anticipar la adquisición o devolución de copias críticas.

---

## ⚡ Pruebas de Carga y Tráfico

El script `loadtesting/stress.sh` permite generar tráfico parametrizado hacia la biblioteca:

```bash
# Sintaxis general
bash loadtesting/stress.sh [-u URL] [-n CANTIDAD] [-p LIBRO] [-d SEGUNDOS] [-c SOCIO]

# Ejemplos:
# 20 préstamos del libro L001 espaciados cada 0.2 segundos
bash loadtesting/stress.sh -n 20 -p L001 -d 0.2

# Prueba de saturación de copias del libro estrella L003
bash loadtesting/stress.sh -n 15 -p L003 -d 0.5 -c "alumno-ingenieria"
```

---

## 🧠 Lecciones Aprendidas y Problemas Conocidos

1. **Minikube con driver y container-runtime Docker**:
   En Windows 11 con WSL2/Docker Desktop, Minikube debe inicializarse con `--container-runtime=docker` y `--kubernetes-version=v1.34.0`. Con `containerd` falla el comando `docker-env` (error `SSH_AGENT_START`), y con versiones de Kubernetes superiores a v1.36 pueden suscitarse inconsistencias en el kube-apiserver local.
2. **Límite de memoria en Docker Desktop**:
   El motor Docker en Windows típicamente cuenta con ~7.4 GB asignados. Iniciar Minikube con `--memory=5120` es ideal para dar cabida a los microservicios Java y al stack de Prometheus, asegurando que no compita con otros perfiles encendidos.
3. **Ejecución de scripts con Git Bash**:
   Ejecutar scripts `.sh` siempre mediante `C:\Program Files\Git\bin\bash.exe`, ya que el comando `bash` por defecto en PowerShell invoca la instancia aislada de WSL, la cual no tiene visibilidad directa de los sockets de Docker Desktop ni de Minikube en el host.
4. **Manejo de JSON y comillas en PowerShell**:
   En PowerShell, no concatenar comandos con `&&` ni pasar cadenas JSON complejas con comillas internas en `-d "..."`. Lo robusto es escribir el JSON a un archivo temporal (`Out-File -Encoding ascii`) y referenciarlo con `curl.exe --data "@archivo.json"`.
5. **Naturaleza Distroless de la imagen de Prometheus**:
   La imagen de Prometheus Operator es *distroless* (no cuenta con `sh`, `ls`, `curl` ni utilitarios de shell en el contenedor). Para consultar el estado de targets, reglas o alertas, se debe acceder a través de su API HTTP (`/api/v1/rules`, `/api/v1/alerts`, `/targets`) vía `kubectl port-forward`.
6. **Ventanas temporales en alertas PromQL (`increase`)**:
   Las reglas de alerta basadas en incrementos (`increase(...[1m]) > 2`) requieren que la ráfaga de tráfico erróneo ocurra de forma inmediata dentro de dicha ventana para que Prometheus compute la tasa y transicione la alerta de `Pending` a `Firing`.

---

## 🧹 Limpieza del Entorno

Para desinstalar los microservicios, el stack de monitoreo y cerrar los port-forwards locales, conservando el perfil de Minikube:

```bash
bash scripts/teardown.sh
```

Si deseas destruir por completo el clúster local de Minikube perteneciente al perfil `biblioteca` (sin afectar jamás el perfil `minikube`):

```bash
bash scripts/teardown.sh --cluster
```

---
*Proyecto de Observabilidad y Monitoreo de Microservicios con Kubernetes, Prometheus y Grafana.*
