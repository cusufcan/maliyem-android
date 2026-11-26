package com.mercan.maliyem.data.receipt

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptParserTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val parser = ReceiptParser(clock)

    @Test fun migrosPrefersFinalTotalOverTaxAndCashTendered() {
        assertEquals(
            ReceiptSuggestion(41_285L, LocalDate.of(2026, 11, 26), "Migros Ticaret A.Ş"),
            parse("""
                MİGROS TİCARET A.Ş.
                Mah. 24. Cad. No:16
                TARİH : 26.11.2026   SAAT : 12:04
                FİŞ NO: 0082
                SEBZE           *155,00
                SÜT              *82,90
                EKMEK            *39,95
                TEMİZLİK        *134,00
                TOPKDV           *33,74
                TOPLAM          *412,85
                NAKİT           *500,00
                PARA ÜSTÜ        *87,15
            """),
        )
    }

    @Test fun toleratesOcrSpacesAfterDecimalAndDateSeparators() {
        assertEquals(
            ReceiptSuggestion(200_564L, LocalDate.of(2026, 11, 25), "Opet"),
            parse("""
                OPET
                ATASEHIR AKARYAKIT A.S.
                25.11. 2026 09:03
                LITRE: 38,21 x 52,49
                TOPLAM *2.005, 64
                KREDI KARTI *2.005, 64
            """),
        )
    }

    @Test fun bimPrefersLabeledDateAndReadsAmountOnNextLine() {
        assertEquals(
            ReceiptSuggestion(123_456L, LocalDate.of(2026, 10, 25), "Bim Birleşik Mağazalar"),
            parse("""
                PERAKENDE SATIŞ FİŞİ
                BİM BİRLEŞİK MAĞAZALAR
                Tel: (0216) 555 11 22
                21.10.2026
                tarıh: 25/10/2026
                ARA TOPLAM     1.534,56
                GENEL TOPLAM
                *1.234,56 TL
                KDV TOPLAMI       82,32
            """),
        )
    }

    @Test fun a101SupportsNumericBrandAndShortYear() {
        assertEquals(
            ReceiptSuggestion(12_345L, LocalDate.of(2026, 11, 18), "A101 Yeni Mağazacılık"),
            parse("""
                0010102
                A101 YENİ MAĞAZACILIK
                Z NO: 336
                18.11.26  16:45
                TUTAR: 123,45 TL
                KREDİ KARTI: 123,45 TL
            """),
        )
    }

    @Test fun restaurantPrefersPayableAndRejectsDiscountAndPercentage() {
        assertEquals(
            ReceiptSuggestion(81_000L, LocalDate.of(2026, 11, 24), "Çınar Lokantası"),
            parse("""
                HOŞ GELDİNİZ
                ÇINAR LOKANTASI
                TARİH: 24-11-2026
                Izgara 480,00
                İçecek 35,00
                KDV %10,00
                ARA TOPLAM 900,00
                İNDİRİM -90,00
                ÖDENECEK ₺ 810.00
            """),
        )
    }

    @Test fun fuelUsesLargestMoneyWhenFinalLabelIsMissing() {
        assertEquals(
            ReceiptSuggestion(123_456L, LocalDate.of(2026, 11, 5), "Petrol Ofisi"),
            parse("""
                PETROL OFİSİ
                VKN 0440198765
                2026-11-05 07:16
                PLAKA 35 ABC 123
                MOTORİN 250,000 LT
                BİRİM FİYAT 48,45
                KREDİ KARTI ₺ 1234.56
            """),
        )
    }

    @Test fun messyOcrUsesBoundsAndRecognizesBothTotalTypos() {
        for (label in listOf("T0PLAM", "TOPLAN")) {
            assertEquals(
                ReceiptSuggestion(9_875L, LocalDate.of(2026, 11, 11), "Migros"),
                parser.parse(listOf(
                    ReceiptLine("NAKİT 100,00", 220),
                    ReceiptLine("MIGROS", 8),
                    ReceiptLine("98,75", 184),
                    ReceiptLine("TARİH", 44),
                    ReceiptLine(label, 179),
                    ReceiptLine("11.11.26", 68),
                    ReceiptLine("TOPKDV 08,25", 158),
                )),
            )
        }
    }

    @Test fun rejectsValuesOverLimitAndAcceptsExactLimit() {
        assertEquals(1_000_000_000L, parse("TOTAL 10.000.000,00").amountMinor)
        assertNull(parse("TOTAL 10.000.000,01\nNAKİT 999999999999999999999999.00").amountMinor)
        assertEquals(1_250L, parse("TOPLAM 10.000.000,01\nEKMEK 12,50").amountMinor)
    }

    @Test fun supportsWholeAmountsWithCurrencyAndBothGroupingConventions() {
        for (amount in listOf("*1234", "₺ 1234", "1.234 TL", "TRY 1,234", "1 234,00")) {
            assertEquals(amount, 123_400L, parse("TOPLAM $amount").amountMinor)
        }
        assertEquals(123_456L, parse("TOTAL 1,234.56").amountMinor)
        assertEquals(25L, parse("TOTAL 0,25").amountMinor)
    }

    @Test fun ignoresPhoneDateQuantityZeroNegativeAndPercentForAmount() {
        assertNull(parse("""
            TARİH 26.11.2026
            0512 909 11 23
            FİŞ NO 153454
            1,000 kg
            PROMOSYON -12,00
            INDIRIM %12,50
            KDV 12,50%
            ADET 12
            TOPLAM 0,00
        """).amountMinor)
    }

    @Test fun doesNotBorrowTaxAmountWhenTotalHasNoValue() {
        assertEquals(21_500L, parse("""
            TOPLAM:
            TOPKDV   12,00
            KART    215,00
        """).amountMinor)
    }

    @Test fun dateWindowIsInclusiveAndCalendarValidationIsStrict() {
        assertEquals(LocalDate.of(2021, 11, 27), parse("27.11.2021").date)
        assertEquals(LocalDate.of(2026, 11, 28), parse("28.11.2026").date)
        assertEquals(LocalDate.of(2024, 2, 29), parse("TARİH 29.02.2024").date)
        for (date in listOf("26.11.2021", "29.11.2026", "31.02.2026", "29.02.2025", "11.12/2025")) {
            assertNull(date, parse("TARİH $date").date)
        }
    }

    @Test fun skipsInvalidLabeledDateAndContinuesLooking() {
        assertEquals(LocalDate.of(2026, 11, 25), parse("""
            24.11.2026
            TARİH 30.11.2026
            TARİH 31.02.2026
            TARIH 25.11.2026
        """).date)
    }

    @Test fun merchantSkipsMetadataAndHasFortyCharacterLimit() {
        assertEquals("Çok Uzun Bir İşletme Adı Olan Örnek Mağa", parse("""
            Perakende satış fişi
            Cumhuriyet Mah. 1. Cad.
            +90 (232) 555 11 22
            VKN 1234567890
            FİŞ NO 45
            Z NO 22
            ÇOK UZUN BİR İŞLETME ADI OLAN ÖRNEK MAĞAZA
            TOPLAM 123,45
        """).merchant)
        assertNull(parse("ADRES: Merkez Mah.\nTARİH 23.11.2026\nEKMEK 42,50").merchant)
    }

    @Test fun emptyOrMeaninglessTextYieldsNoSuggestions() {
        assertEquals(ReceiptSuggestion(null, null, null), parser.parse(emptyList()))
        assertEquals(ReceiptSuggestion(null, null, null), parse("123\n*?!\nFİŞ NO 41"))
    }

    private fun parse(text: String) = parser.parse(text.trimIndent().lines().map { ReceiptLine(it) })
}
