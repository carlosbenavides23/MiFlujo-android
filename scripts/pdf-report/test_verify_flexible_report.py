import unittest

from verify_flexible_report import DETAIL, NAME, verify_extracted


def fixture() -> str:
    long_amount = "C$ 92,233,720,368,547,758 07"
    first = [
        *([long_amount] * 5),
        f"{NAME.rsplit(' ', 1)[0]}  {long_amount}",
        "NOMBRECOMPLETO (archivada)",
        "Servicio domiciliario",
        f"{NAME} (archivada) / Servicio domiciliario",
        *DETAIL[:72],
    ]
    last = [*DETAIL[154:], "Sin categoría", "Sin categoría", *(["US$ 1.01"] * 4)]
    pages = (first, DETAIL[72:154], last)
    return "".join(
        "\n".join(["MiFlujo - Reporte mensual", "febrero 2024", *lines, f"Página {n} de 3"]) + "\n\f"
        for n, lines in enumerate(pages, 1)
    )


class FlexiblePdfVerificationTest(unittest.TestCase):
    def test_accepts_complete_detail_and_both_poppler_decimal_representations(self):
        self.assertEqual((3, 1, 3), verify_extracted(fixture()))
        self.assertEqual((3, 1, 3), verify_extracted(fixture().replace("758 07", "758.07")))

    def test_rejects_missing_duplicate_reordered_or_incomplete_detail(self):
        source = fixture()
        line = DETAIL[72]
        following = DETAIL[73]
        for malformed in (
            source.replace(line + "\n", "", 1),
            source.replace(line, line + "\n" + line, 1),
            source.replace(line + "\n" + following, following + "\n" + line, 1),
            source.replace("INICIODETALLE\n", "", 1),
            source.replace("FINDETALLE\n", "", 1),
            source.replace(line, line.removesuffix(" detallada"), 1),
        ):
            with self.subTest(malformed=malformed[:60]), self.assertRaises(ValueError):
                verify_extracted(malformed)

    def test_rejects_truncated_labels_and_invalid_amounts(self):
        source = fixture()
        for original, defective in (
            ("NOMBRECOMPLETO", "NOMBRECOMP"),
            ("Servicio domiciliario", "Servicio domiciliar"),
            ("758 07", "758 0"),
            ("758 07", "758 077"),
            ("758 07", "758 08"),
            ("US$ 1.01", "US$ 1."),
            ("Sin categoría", "Sin catego"),
        ):
            with self.subTest(defective=defective), self.assertRaises(ValueError):
                verify_extracted(source.replace(original, defective, 1))
        with self.assertRaises(ValueError):
            verify_extracted(source.replace("Sin categoría", "Sin categoría\nUS$ 1.01", 1))

    def test_rejects_moving_lines_to_the_wrong_page_order_or_skipping_a_page(self):
        source = fixture()
        first, middle, last, _ = source.split("\f")
        reordered = middle.replace("Página 2 de 3", "Página 1 de 3") + "\f" + first.replace(
            "Página 1 de 3", "Página 2 de 3",
        ) + "\f" + last + "\f"
        with self.assertRaises(ValueError):
            verify_extracted(reordered)
        for line in DETAIL[72:154]:
            middle = middle.replace(line + "\n", "", 1)
        # The detail is intact and in sequence, but the middle page interrupts it.
        last = last.replace("febrero 2024\n", "febrero 2024\n" + "\n".join(DETAIL[72:154]) + "\n", 1)
        with self.assertRaisesRegex(ValueError, "no continúa"):
            verify_extracted("\f".join((first, middle, last, "")))


if __name__ == "__main__":
    unittest.main()
