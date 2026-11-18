"""Wspolne zrodlo wektorowej ikony oraz banera Android TV; bez osadzonych fontow."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GLYPHS = {
    'A':['01110','11011','11011','11111','11011','11011','11011'],
    'C':['11111','11000','11000','11000','11000','11000','11111'],
    'D':['11110','11011','11011','11011','11011','11011','11110'],
    'E':['11111','11000','11000','11110','11000','11000','11111'],
    'H':['11011','11011','11011','11111','11011','11011','11011'],
    'I':['111','111','111','111','111','111','111'],
    'K':['11011','11011','11110','11100','11110','11011','11011'],
    'N':['11011','11111','11111','11111','11011','11011','11011'],
    'O':['01110','11011','11011','11011','11011','11011','01110'],
    'P':['11110','11011','11011','11110','11000','11000','11000'],
    'R':['11110','11011','11011','11110','11110','11011','11011'],
    'T':['11111','01110','01110','01110','01110','01110','01110'],
    'V':['11011','11011','11011','11011','11011','01110','00100'],
    ' ':['000','000','000','000','000','000','000'],
}


def lettering(value: str, x: float, y: float, scale: float) -> str:
    paths = []
    for char in value:
        glyph = GLYPHS[char]
        for row, line in enumerate(glyph):
            for column, pixel in enumerate(line):
                if pixel == '1':
                    left, top = x + column * scale, y + row * scale
                    paths.append(f'M{left:g},{top:g}h{scale:g}v{scale:g}h{-scale:g}Z')
        x += (len(glyph[0]) + 1) * scale
    return ''.join(paths)


def write(name: str, width: int, height: int, shapes: list[tuple[str, str]]) -> None:
    xml = f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width}dp" android:height="{height}dp" android:viewportWidth="{width}" android:viewportHeight="{height}">\n'
    xml += '\n'.join(f'    <path android:fillColor="{color}" android:pathData="{path}"/>' for color, path in shapes)
    (ROOT / 'app/res/drawable' / (name + '.xml')).write_text(xml + '\n</vector>\n', encoding='utf-8')
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{width*4}" height="{height*4}" viewBox="0 0 {width} {height}">'
    svg += ''.join(f'<path fill="{color}" d="{path}"/>' for color, path in shapes) + '</svg>'
    target = ROOT / 'docs/artwork'
    target.mkdir(parents=True, exist_ok=True)
    (target / (name + '.svg')).write_text(svg, encoding='utf-8')


def main() -> None:
    icon = [
        ('#26392D', 'M18,0H78Q96,0 96,18V78Q96,96 78,96H18Q0,96 0,78V18Q0,0 18,0Z'),
        ('#101A14', 'M18,1H78Q95,1 95,18V78Q95,95 78,95H18Q1,95 1,78V18Q1,1 18,1Z'),
        ('#53FC18', lettering('KICK', 13.5, 24, 3.3)),
        ('#53FC18', 'M17,57H79Q85,57 85,63V74Q85,80 79,80H17Q11,80 11,74V63Q11,57 17,57Z'),
        ('#0C160D', lettering('PATCHER', 18.25, 63.5, 1.45)),
    ]
    write('icon', 96, 96, icon)
    write('wordmark', 110, 42, [('#53FC18', lettering('KICK', 2.5, 3.5, 5))])
    banner = [
        ('#0D1510', 'M0,0H320V180H0Z'),
        ('#1A2C20', 'M226,0H320V180H180Z'),
        ('#53FC18', lettering('KICK', 24, 35, 5.5)),
        ('#D4E0D6', lettering('ANDROID TV', 24, 86, 1.5)),
        ('#53FC18', 'M30,112H147Q153,112 153,118V141Q153,147 147,147H30Q24,147 24,141V118Q24,112 30,112Z'),
        ('#0D1510', lettering('PATCHER', 39, 122, 2.4)),
        ('#58755F', 'M226,48H270L291,69V127H226ZM229,51V124H288V72H267V51Z'),
        ('#53FC18', 'M242,85H254V73H262V85H274V93H262V105H254V93H242Z'),
    ]
    write('banner', 320, 180, banner)


if __name__ == '__main__':
    main()
