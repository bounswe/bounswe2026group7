# Production'a Geçmeden Önce Yapılacaklar

## 1. Environment Variables

Sunucuda aşağıdaki env variable'ları güvenli şekilde set et (`.env` dosyası veya sunucu secrets manager):

### Veritabanı
- [ ] `POSTGRES_DB` — production DB adı
- [ ] `POSTGRES_USER` — güçlü bir kullanıcı adı
- [ ] `POSTGRES_PASSWORD` — güçlü bir şifre (default `group7pass` kullanma)

### JWT
- [ ] `JWT_SECRET` — en az 256-bit güçlü, rastgele üretilmiş Base64 string
  ```bash
  # Yeni secret üretmek için:
  openssl rand -base64 32
  ```
- [ ] `JWT_EXPIRATION` — uygun süre (default 86400000 = 1 gün)

### Email (Gerçek SMTP)
- [ ] `SPRING_MAIL_HOST` — örn. `smtp.gmail.com`
- [ ] `SPRING_MAIL_PORT` — örn. `587`
- [ ] `SPRING_MAIL_USERNAME` — gönderici email adresi
- [ ] `SPRING_MAIL_PASSWORD` — uygulama şifresi (Gmail için "App Password" kullan)
- [ ] `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` — `true`
- [ ] `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` — `true`
- [ ] `APP_MAIL_FROM` — kullanıcıya görünecek gönderici adresi (örn. `noreply@group7.com`)

### Uygulama
- [ ] `APP_BASE_URL` — production domain (örn. `https://api.group7.com`)
  - Bu değer doğrulama emailindeki linkte kullanılır
- [ ] `VERIFICATION_TOKEN_EXPIRY_HOURS` — isteğe göre ayarla (default 24)
- [ ] `VERIFICATION_RESEND_MAX_PER_HOUR` — isteğe göre ayarla (default 3)

---

## 2. Docker Compose

- [ ] `mailhog` servisini `docker-compose.yml`'den kaldır (production'da gereksiz)
- [ ] `backend` servisindeki `depends_on` içinden `mailhog` satırını kaldır
- [ ] Frontend için `VITE_BACKEND_URL` değerini production URL'iyle güncelle

---

## 3. Güvenlik

- [ ] `spring.jpa.show-sql=true` → `false` yap (`application.properties`)
- [ ] `spring.jpa.hibernate.ddl-auto=validate` olduğunu doğrula (zaten doğru)
- [ ] HTTPS kullan — backend önüne nginx veya load balancer koy
- [ ] CORS ayarlarını yap — sadece production frontend domain'ine izin ver
- [ ] `.env` dosyasını `.gitignore`'a ekle, repoya commit'leme

---

## 4. Veritabanı

- [ ] DB volume'ünün düzenli yedeği alındığından emin ol
- [ ] Production DB'nin dışarıdan erişilemez olduğunu doğrula (sadece backend container erişebilmeli)

---

## 5. Test

- [ ] `./mvnw test` — tüm testlerin geçtiğini doğrula
- [ ] Manuel test: register → email gel → verify → login
- [ ] Expired token testi: token süresini kıs, link'e tıkla, hata mesajı doğru mu?
- [ ] Rate limit testi: aynı email için 4. resend'de 429 benzeri hata geliyor mu?
