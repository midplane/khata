# Rozha One

- Source: https://github.com/google/fonts/tree/main/ofl/rozhaone
- Copyright (c) 2014, Indian Type Foundry. Licensed under the SIL Open Font License 1.1 (see `OFL.txt`).

Used in Khata for:

- `app/src/main/res/font/rozha_one_khata.ttf`: subset of RozhaOne-Regular containing only the glyphs for "खाता"
  (`pyftsubset RozhaOne-Regular.ttf --text="खाता" --layout-features='*'`), used for the in-app title.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml`: the ₹ is the
  glyph outline of U+20B9 from this font, converted to vector path data.
