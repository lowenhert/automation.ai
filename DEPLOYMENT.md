# Tata Play Reporting Dashboard — Deployment Guide

## Option A: Standalone Frontend (http-server) — Quick LAN Sharing

### 1. Update the LAN IP
Edit `src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiUrl: 'http://<YOUR_LAN_IP>:8080/api'
};
```
Find your LAN IP: `ipconfig` → IPv4 Address

### 2. Build & Serve
```bash
npm run deploy
```
Access from any device on the network: `http://<YOUR_LAN_IP>:4200`

---

## Option B: Serve Angular from Spring Boot (Single Port, Production-Ready)

### 1. Build Angular
```bash
npm run build:prod
```
Output: `dist/ng-tailadmin/browser/`

### 2. Copy dist into Spring Boot
Copy all files from `dist/ng-tailadmin/browser/` into:
```
<SpringBootProject>/src/main/resources/static/
```

### 3. Add SPA Controller to Spring Boot
Create `SpaController.java` so Angular routing (deep links) works:
```java
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {
    // Forward all non-file, non-api routes to Angular's index.html
    @RequestMapping(value = {
        "/", "/{path:[^\\.]*}", "/{path:^(?!api).*$}/**/{subpath:[^\\.]*}"
    })
    public String redirect() {
        return "forward:/index.html";
    }
}
```

### 4. Add CORS + Security Config to Spring Boot
```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false); // set true if using cookies/auth headers
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```
Wire it into your `SecurityFilterChain`:
```java
http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

### 5. Run Spring Boot
```bash
mvn spring-boot:run
# or
java -jar target/<your-app>.jar
```
Access: `http://<YOUR_LAN_IP>:8080`

---

## Pushing to GitHub

```bash
# Add your remote (replace URL with your repo)
git remote add origin https://github.com/<your-username>/<repo-name>.git
git branch -M main
git push -u origin main
```

For future changes:
```bash
git add .
git commit -m "your message"
git push
```

---

## Environment Files Summary

| File | Used When |
|------|-----------|
| `src/environments/environment.ts` | `ng serve` / dev build |
| `src/environments/environment.prod.ts` | `npm run build:prod` |

**Never commit production secrets.** The `environment.prod.ts` is tracked in git here because it only contains a LAN IP — adjust `.gitignore` if this ever holds API keys.
