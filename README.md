# E-Okul Foto Studio (Android)

Telefonla öğrenci fotoğrafı çek → e-Okul ölçüsüne (133×171) otomatik kırp → öğrenci numarası ve adıyla kaydet.

## Nasıl kullanılır
1. **Kayıt klasörü seç** (ana ekrandaki uyarı veya ⚙ Ayarlar). Tüm çıktılar bu klasörün altına yazılır.
2. **＋ Sınıf ekle** → sınıf adı (ör. 9-A) → listeyi yapıştır. e-Okul/Excel'den kopyalanan
   "numara + ad soyad" sütunları (sekme, ; , | - boşluk ayraçlı) otomatik algılanır; önizlemede hatalı satırlar görünür.
3. Sınıfa gir → öğrenciye dokun (veya **📷 Seri çekim**) → kamerada baş ovalini yerleştir → çek →
   önizlemeyi kontrol et → **Kaydet**. Seri çekimde otomatik sıradaki öğrenciye geçilir.

## Kayıt yapısı
```
<Kayıt klasörü>/9-A/12345-Ayşe Yılmaz.jpg          (133×171, arşiv adı)
<Kayıt klasörü>/9-A/Orijinaller/12345-Ayşe Yılmaz.jpg   (tam çözünürlüklü orijinal)
```
Çıktı: 4:4:4 JPEG, kalite 100, 300 DPI, sRGB profili gömülü, en az 20 KB.

## APK derleme (GitHub Actions)
1. GitHub'da yeni (özel) bir depo oluştur, bu klasörün içeriğini yükle (`.github` klasörü dahil).
2. **Actions → Build APK → Run workflow** (veya her push'ta otomatik çalışır).
3. Bitince çalıştırmanın altındaki **Artifacts → EOkulFotoStudio-apk** dosyasını indir, zip'ten çıkan `app-debug.apk`'yı telefona kur
   ("bilinmeyen kaynaklardan yükleme" izni gerekir).

## Önemli not
Kotlin/Compose/CameraX kodu bu ortamda derlenemedi (Android SDK'ya erişim yok). Çekirdek algoritmalar (kırpma, yeniden
boyutlandırma, JPEG kodlayıcı, liste ayrıştırıcı) Java ile birebir test edildi. İlk derlemede hata çıkarsa Actions
günlüğünü (hata satırlarını) yapıştır, düzeltelim.

## Eklenenler (Faz 2)
- Kalite uyarıları: bulanık, çok karanlık / parlak, baş eğik, yüz dönük (inceleme ekranında).
- Saç tepesi ölçümü: ML Kit kişi maskesi (yedek: arka plan analizi), saç üstü boşluk varsayılan %3.
- Sınıf menüsü (⋮): eksik listesini paylaş, e-Okul ZIP (içinde `12345.jpg` adlı dosyalar), dosya adlarını güncelle.
- Ayarlar: Yedekle / Geri yükle (sınıf listeleri + küçük fotoğraflar).

## Faz 3
- Kamerada anlık yüz tespiti: yeşil/sarı yüz kutusu ve yönlendirme ("Biraz yaklaşın", "Başı dik tutun"…).
- Otomatik odak/pozlama: yüze sürekli, ayrıca ekrana dokunarak.
- Otomatik çekim (anahtar): yüz kılavuza oturup sabit durunca 1,2 sn geri sayım ve çekim; deklanşör sesi + titreşim.
  Seri çekimde bir önceki öğrenci çerçevedeyken yanlışlıkla çekmemek için yüz çerçeveden çıkıp girince silahlanır.
- PIN kilidi (Ayarlar → PIN): açılışta ve 30 sn'den uzun arka planda kalınca sorulur.
- Listeyi CSV/TXT dosyadan alma (UTF-8 ve Windows-1254 desteklenir).
