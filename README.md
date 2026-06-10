# 🤖 IAgente

IAgente es un asistente inteligente desarrollado en **Java 17** y **Spring Boot** capaz de mantener memoria persistente de los usuarios, consultar información en Internet y responder utilizando contexto histórico y conocimiento personalizado.

El objetivo del proyecto es construir un agente conversacional que combine:

* 🧠 Memoria a largo plazo
* 💬 Historial conversacional
* 🌐 Búsquedas web
* 🤖 Modelos LLM
* ⚡ Enrutamiento inteligente de consultas

---

# 🚀 Características

## 🧠 Memoria Persistente

IAgente es capaz de recordar información relevante de cada usuario:

* Nombre
* Edad
* Ciudad
* Trabajo
* Gustos
* Pareja
* Hijos
* Fechas de nacimiento

Ejemplo:

Usuario:

> Mi hijo Pablo nació el 14 de marzo de 2013

Memoria almacenada:

```text
persona:hijo:pablo:fecha_nacimiento
```

Posteriormente:

> ¿Cuándo nació Pablo?

Respuesta:

> Pablo nació el 14 de marzo de 2013.

````

---

## 🧭 Router Inteligente

Antes de responder, IAgente decide automáticamente qué recursos utilizar:

| Recurso | Uso |
|----------|----------|
| Memoria | Datos personales conocidos |
| Historial | Conversaciones recientes |
| Web | Información actualizada |
| LLM | Razonamiento y generación de respuestas |

Esto reduce costes y mejora el rendimiento.

---

## 🌐 Integración con Internet

Cuando la consulta requiere información actualizada:

- Noticias
- Tiempo
- Información pública
- Búsquedas generales

IAgente puede realizar búsquedas web y utilizar los resultados para responder.

---

## 🗂️ Extracción Automática de Memoria

Un extractor basado en LLM analiza los mensajes del usuario y convierte información relevante en memoria estructurada.

Ejemplo:

```text
Mercedes nació el 4 de octubre de 1978
````

Se transforma en:

```json
{
  "subject": "pareja",
  "attribute": "fecha_nacimiento",
  "value": "4 de octubre de 1978"
}
```

---

# 🏗️ Arquitectura

```text
Usuario
   │
   ▼
AgentService
   │
   ├── Memory Extractor
   │
   ├── Query Router
   │
   ├── Memory Service
   │
   ├── Web Search Service
   │
   └── LLM Service
            │
            ▼
       Respuesta final
```

---

# 🛠️ Tecnologías

* Java 17
* Spring Boot 3
* Spring AI
* Ollama
* Maven
* JPA / Hibernate
* H2 / PostgreSQL
* Jackson

---

# 📦 Instalación

Clonar repositorio:

```bash
git clone https://github.com/usuario/iagente.git
```

Entrar en el proyecto:

```bash
cd iagente
```

Compilar:

```bash
mvn clean install
```

Ejecutar:

```bash
mvn spring-boot:run
```

---

# 📋 Ejemplos

## Guardar memoria

Usuario:

```text
Mi hijo Pablo nació el 14 de marzo de 2013
```

IAgente:

```text
Información almacenada correctamente.
```

---

## Recuperar memoria

Usuario:

```text
¿Cuándo nació Pablo?
```

IAgente:

```text
Pablo nació el 14 de marzo de 2013.
```

---

## Consulta web

Usuario:

```text
¿Qué tiempo hará mañana en Madrid?
```

IAgente:

```text
Mañana en Madrid se esperan máximas de 31°C y cielos despejados.
```

---

# 🎯 Objetivos del Proyecto

* Construir un asistente personal persistente.
* Minimizar alucinaciones mediante memoria estructurada.
* Optimizar el uso de modelos LLM.
* Crear una arquitectura modular y extensible.
* Experimentar con agentes inteligentes híbridos.

---

# 📄 Licencia

Este proyecto se distribuye bajo licencia MIT.

---

# 👨‍💻 Autor

**Julián Gómez Fernández**

Ingeniero de Software especializado en Java, Spring Boot e Inteligencia Artificial aplicada.
