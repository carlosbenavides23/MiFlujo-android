"""Check the synthetic FlexibleReportPdfGeneratorTest fixture using Poppler."""
import argparse
import re
import subprocess
import unicodedata
from pathlib import Path

DETAIL = ["INICIODETALLE", *(
    f"LINEA{number:03} Servicio con descripción detallada" for number in range(1, 241)
), "FINDETALLE"]
NAME = "Servicios con nombre extenso para confirmar el ajuste de línea NOMBRECOMPLETO"
MARKER = re.compile(r"\b(?:INICIODETALLE|FINDETALLE|LINEA[0-9]+)\b")
AMOUNT = re.compile(r"(?:US|C)\$ [0-9][0-9,]*[. ][0-9]{2}(?![\w.,])")
# The reference PDF renders the point, although Poppler extracts it as a word gap.
LONG_AMOUNT = re.compile(r"C\$ 92,233,720,368,547,758[. ]07(?![\w.,])")
DOLLAR_AMOUNT = re.compile(r"US\$ 1[. ]01(?![\w.,])")


def verify_extracted(extracted: str) -> tuple[int, int, int]:
    # Poppler uses a terminal form feed as well as form feeds between pages.
    pages = extracted.removesuffix("\f").split("\f")
    if len(pages) < 3 or any(not page.strip() for page in pages):
        raise ValueError("Se esperaban al menos tres páginas con contenido")
    body = []
    page_text = []
    for number, page in enumerate(pages, 1):
        normalized = unicodedata.normalize("NFC", page)
        lines = [" ".join(line.split()) for line in normalized.splitlines() if line.strip()]
        footer = f"Página {number} de {len(pages)}"
        if lines[:2] != ["MiFlujo - Reporte mensual", "febrero 2024"] or lines[-1:] != [footer]:
            raise ValueError(f"Encabezado o pie incompleto en página {number}")
        body.extend((number, line) for line in lines[2:-1])
        page_text.append("\n".join(lines[2:-1]))
    markers = [(page, match.group()) for page, line in body for match in MARKER.finditer(line)]
    if [marker for _, marker in markers] != [line.split()[0] for line in DETAIL]:
        raise ValueError("Marcador ausente, duplicado o fuera de orden en el detalle")
    first = next((index for index, (_, line) in enumerate(body) if line == DETAIL[0]), None)
    last = next((index for index, (_, line) in enumerate(body) if line == DETAIL[-1]), None)
    if first is None or last is None:
        raise ValueError("Marcador inicial o final alterado")
    if [line for _, line in body[first:last + 1]] != DETAIL:
        raise ValueError("El contenido de los renglones del detalle no coincide")
    start_page, end_page = markers[0][0], markers[-1][0]
    if end_page <= start_page or {page for page, _ in markers} != set(range(start_page, end_page + 1)):
        raise ValueError("El detalle no continúa en todas las páginas entre sus extremos")
    labels = " ".join(" ".join(AMOUNT.sub("", text).split()) for text in page_text)
    for label in (NAME, "Servicio domiciliario", "Sin categoría"):
        if len(re.findall(rf"(?<!\w){re.escape(label)}(?!\w)", labels)) != 2:
            raise ValueError(f"El nombre {label!r} debe aparecer completo dos veces")
    if [len(LONG_AMOUNT.findall(text)) for text in page_text] != [
        6 if page == start_page else 0 for page in range(1, len(pages) + 1)
    ]:
        raise ValueError("Importes largos incompletos, repetidos o fuera de su página")
    if sum(len(DOLLAR_AMOUNT.findall(text)) for text in page_text) != 4:
        raise ValueError("Importes en dólares incompletos o repetidos")
    return len(pages), start_page, end_page


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pdf", type=Path)
    args = parser.parse_args()
    try:
        result = subprocess.run(
            ["pdftotext", "-layout", "-enc", "UTF-8", str(args.pdf.resolve()), "-"],
            check=True, capture_output=True, encoding="utf-8",
        )
        pages, start, end = verify_extracted(result.stdout)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        parser.exit(1, f"PDF inválido para esta prueba: {error}\n")
    print(f"OK: {pages} páginas, 242 marcadores en orden (páginas {start}–{end}), nombres e importes completos")


if __name__ == "__main__":
    main()
