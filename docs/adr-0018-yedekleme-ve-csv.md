# ADR-0018 · Kullanıcının seçtiği dosyalarla yedekleme ve dışa aktarma

- **Durum:** Uygulandı (2026-11-26)
- **Bağlam:** Kullanıcı kayıtlarını ve kategorilerini saklayabilmeli, geri getirebilmeli ve bir hesap tablosunda açabilmeli.
- **Karar:** Android'in sistem dosya seçicisi kullanılır. JSON biçimi Room şemasından bağımsız sürümlenir. Yeni listeler boş varsayılanla eklenir ve tanınmayan alanlar okunurken atlanır. Geri yükleme dosyayı önce doğrular, kullanıcıya dosyadaki sayıları gösterir ve onaydan sonra iki tabloyu aynı Room işleminde özgün kimlikleriyle değiştirir. CSV, uygulamanın dilindeki tür ve kategori adlarını, noktalı ondalık tutarları ve UTF-8 BOM'u kullanır.
- **Neden:** Sabit bir dosya biçimi eski yedekleri okunabilir kılar. Atomik yazım, hata durumunda mevcut veriyi korur. Sistem dosya seçicisi uygulamanın depolama erişimini kullanıcının seçtiği dosyayla sınırlar.
- **Alternatifler:** Veritabanı dosyasını kopyalamak yedeği Room şemasına bağlar. Satırları birleştirmek kimlik çakışmalarını çözmek için ek kurallar gerektirir.
- **Bedeli:** Dışa aktarım tutarlı bir anlık görüntü alır. Geri yükleme, doğrulanan dosyayı onay süresince bellekte tutar.
