# Arka Ekran Saat — Xiaomi 17 Pro / 17 Pro Max

Xiaomi 17 Pro / Pro Max'in arkadaki "Magic Back Screen" ekranında **seçtiğiniz duvar kağıdını ve büyük dijital saati** gösteren, **root gerektirmeyen** (Shizuku tabanlı) bir Android uygulaması.

## Neden Shizuku?

HyperOS 3, normal üçüncü parti uygulamaların arka ekranda açılmasını engeller (hata kodu 102) ve bu ekran için resmî bir SDK yoktur. Bu uygulama, shell (ADB) seviyesinde yetki veren [Shizuku](https://github.com/RikkaApps/Shizuku) üzerinden `am` komutlarıyla saat ekranını arka ekranda başlatır ve Xiaomi'nin kendi arka ekran başlatıcısıyla uyumlu çalışır. **Bootloader kilidi açma veya root gerekmez.**

## Kurulum

1. APK'yı edinin: repo'daki GitHub Actions "Build APK" iş akışının artifact'ından (`arka-ekran-saat-debug`) indirin.
2. Telefondan kurun (bilinmeyen kaynaklara izin verin).
3. [Shizuku uygulamasını](https://shizuku.rikka.app/download/) kurun ve başlatın:
   - **Kablosuz hata ayıklama** ile (Ayarlar → Geliştirici seçenekleri → Kablosuz hata ayıklama), şizuku uygulamasındaki adımların takibiyle; veya
   - bir bilgisayardan `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh` ile.
4. "Arka Ekran Saat" uygulamasını açın → **Shizuku izni ver**.
5. **Duvar kağıdı seç** (isteğe bağlı — seçmezseniz sadece saat gösterilir).
6. **Arka ekranda göster**.

## Kullanım

- Saat ekranı arka ekranda açılır; bekçi servisi (bildirim çubuğunda görünür) Xiaomi'nin arka ekranı geri alması durumunda saati tekrar öne getirir.
- **Ekranı açık tut**: arka ekranın kendiliğinden kararmasını geciktirir (pil tüketimini artırır).
- Çıkmak için: uygulamadaki **Kapat** düğmesi, veya arka ekranda **uzun basış**.
- Yalnızca Xiaomi 17 Pro / Pro Max üzerinde test edilmek üzere tasarlanmıştır; diğer cihazlarda arka ekran bulunamazsa uygulama yine de ana ekranda sorunsuz çalışır.

## Derleme (geliştiriciler için)

Gereksinimler: JDK 17, Android SDK (compileSdk 36).

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/`.

## Nasıl çalışır?

- `DisplayFinder` — DisplayManager'dan arka ekrani bulur (ana ekran olmayan, uygun boyutlu ikinci ekran) ve id'yi hatırlar.
- `RearCommands` — Shizuku üzerinden çalıştırılan izole `am` / `input` / `cmd` komutları: arka ekranda activity başlatma, Xiaomi arka ekran başlatıcısını durdurma/geri yükleme, ekranı uyandırma.
- `RearActivity` — arka ekranda tam ekran saat + duvar kağıdı görünümü; yalnızca izin verilen başlatma token'ı ile kalır.
- `KeeperService` — her 3 saniyede `am stack list` ile arka ekranda kimin önde olduğunu denetler; Xiaomi başlatıcısı öne geçerse saati geri getirir.

Kısıt: HyperOS güncellemeleri bu davranışı değiştirebilir; topluluk çözümleriyle aynı sınırlar geçerlidir.

## Lisans

MIT
