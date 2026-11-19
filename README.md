# Harcama Takip

Tamamen çevrimdışı çalışan, reklamsız, **internet izni istemeyen** modern bir Android harcama takip uygulaması.
Kotlin + Jetpack Compose (Material 3) · MVVM · Hilt · Room · DataStore · WorkManager

## Özellikler
- Gelir/gider ekleme (büyük sayısal giriş, kategori, tarih, not, nakit/kart)
- Tarihe göre gruplu liste, kaydırarak silme + geri al, arama, filtre ve sıralama
- Hazır + özel kategoriler (ad, ikon, renk); silinen kategori işlemleri "Diğer"e taşınır
- Aylık genel ve kategori bütçesi, dairesel gösterge, %80 / %100 bildirimi
- Tekrarlayan işlemler (haftalık/aylık/yıllık), otomatik ekleme, "yarın ödemen var" hatırlatması
- Animasyonlu grafikler: kategori halkası (gider/gelir, küçük kategoriler "Diğer" altında), aylık gelir-gider çubukları, dönem uzunluğuna göre günlük/haftalık/aylık trend çizgisi (önceki dönem karşılaştırmalı)
- Birikimli harcama grafiği (bütçe çizgisi + ay sonu tahmini), haftanın günlerine göre ortalama, harcama takvimi (ısı haritası), nakit/kart dağılımı
- Ana ekranda son 7 gün mini grafiği; analizde net tutar ve tasarruf oranı
- Kategoriye dokununca açılan ayrıntı: son 6 ay trendi ve dönemin en büyük işlemleri
- Bütçe ekranında "bu hızla ay sonu" tahmini ve limit aşım uyarısı
- İçgörüler: aynı gün sayısına göre adil dönem karşılaştırması, en çok harcanan kategori, günlük ortalama, sabit giderleri ayıran dönem sonu tahmini
- Aylık grafikte net çizgisi; kategori ayrıntısındaki işleme dokununca düzenleme ekranı açılır
- Performans: analiz yalnızca seçili dönem + önceki dönem işlemlerini okur; aylık toplamlar SQLite'ta (GROUP BY) hesaplanır
- Erişilebilirlik: grafiklerde TalkBack açıklamaları, gider çubuklarında renkten bağımsız çizgi deseni
- CSV dışa/içe aktarma, JSON yedekleme/geri yükleme, tüm verileri silme
- Koyu/açık tema, Android 12+ dinamik renk, para birimi seçimi
- **Hızlı ekle:** işlemi "Şablon olarak kaydet", ana ekranda tek dokunuşla ekle (geri al destekli); sık tekrarlanan işlemler otomatik önerilir
- **Ödeme yöntemi:** Nakit / Banka Kartı / Kredi Kartı + isteğe bağlı kart/hesap etiketi; analizde halka grafik ve etiket kırılımı
- **Gelişmiş filtre:** kategori, ödeme yöntemi, tarih aralığı (hazır kısayollarla), tutar ve sıralama
- **Bütçe bildirimi:** her ay %80 ve %100 eşiği için en fazla 1'er bildirim (Room'da durum takibi)
- **Analiz:** tahmin kartında "nasıl hesaplanır" açıklaması, günlük güvenli harcama limiti, kategori bazlı +% / −% trend ve karşılaştırma grafiği
- **Yedek hatırlatıcısı:** haftalık/2 haftalık/aylık kart + bildirim; dokununca yedek penceresi açılır

## Derleme
1. Android Studio (Koala veya yenisi) ile klasörü aç, Gradle Sync bekle.
2. Debug için: **Run ▶**. APK: `app/build/outputs/apk/debug/`.

## Testler
```bash
./gradlew testDebugUnitTest
```
Hesaplama katmanı (`AnalyticsCalculator`, `RecurrenceUtil`) için birim testler `app/src/test` altındadır.

## İmzalı release APK
```bash
keytool -genkey -v -keystore harcama-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias harcama
cp keystore.properties.example keystore.properties   # şifreleri düzenle
./gradlew assembleRelease
```
Çıktı: `app/build/outputs/apk/release/app-release.apk` → GitHub **Releases** bölümüne yükle.
`keystore.properties` ve `.jks` dosyalarını asla repoya ekleme (.gitignore'da).

## CSV biçimi
`Tarih;Tür;Tutar;Kategori;Not;Ödeme` — örnek: `2026-11-18;Gider;125,50;Market;Haftalık alışveriş;Kart`

## Lisans
MIT
