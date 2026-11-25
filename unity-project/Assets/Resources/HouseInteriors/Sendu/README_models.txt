Cozy Interiors + Builder 1.0.1, models for other engines

FBX/<Room>/ and GLB/<Room>/ hold the same models. Rooms: Shell, Bedroom, Kitchen, Living, Bakery, Cafe, Office, Bathroom, Kids and Decor.
The FBX holds a whole model with its LOD levels as _LOD0 and _LOD1 children. The GLB has one file per level (NAME_LOD0.glb, NAME_LOD1.glb);
models without levels are NAME.glb. Palettes/ holds the four themes, Patterns/ the 16 pattern slices and periods.txt.

Axes and scale
FBX: Y up, 1 unit = 1 m. Fronts face -Z.
GLB: standard glTF, Y up, 1 unit = 1 m. Fronts face +Z.
Pivots are the same as in the Unity pack. Furniture and props have the pivot at the base centre of the footprint. Wall items sit at
the back centre. Ceiling items sit at the top centre. Wall, floor and ceiling pieces of the shell: walls sit at the base of their
start on the centre line and run along +X, floors and ceilings sit at a corner. In a GLB the pivot values are kept in the Unity
object axes, so they differ from the glTF axes.

Material and palette
Every model uses one material with one palette PNG, Palettes/CI_Theme_<Name>.png (256 x 256, a grid of 8 x 8 colour cells of 32 px).
UV0 holds points at cell centres, so each face is one flat colour in any engine. Set the texture filter to nearest and turn mipmaps off;
mipmaps blend neighbouring cells. Themes: swap the palette PNG (Honey, Sage, Walnut, Linen).
The palette alpha is smoothness, not opacity. Do not wire it to alpha or to transparency.
Lamp and fire faces use cells 41 to 43: give them an emissive material. Glass faces use cell 40: give them a transparent material.
The models are flat shaded.

Vertex and UV data (used by the Unity shaders, safe to ignore)
Vertex colour R ambient occlusion, G pattern slice / 15 (in the Unity Soft shader; in the Emissive shader G is a flame mask), B pattern strength, A edge highlight.
Vertex alpha is not opacity. G is per shader, so read it with the shader you write in mind.
UV1 holds metres from the pivot. Divide by the period in Patterns/periods.txt to tile a pattern slice.
UV2 holds a second palette cell B and the finish zone. The finish zone is the whole-number part of UV2.x (0 none, 1 upper wall, 2 lower wall
below 0.9 m); only the inner faces of shell walls carry 1 or 2. The cell B u is the fraction of UV2.x, and UV2.y is the cell B v.
In the Unity Soft shader the pattern texture value mixes the two cells: 0 gives cell A, 255 gives cell B.
Palette cell 0 is the bottom left of the texture, cell = row * 8 + column, with row 0 at the bottom.

Unity only
The shaders, the theme controller, the room builder, the cutaway, the prefabs, the demo and gallery scenes and the layouts need the Unity package.

Licence
Copyright (c) 2026 the author of Cozy Interiors + Builder. Contact: cholak.apps@gmail.com

When you download this pack you get a non-exclusive, worldwide, perpetual licence to use its
models, palettes, patterns, materials, prefabs, layouts, scenes, shaders and scripts, changed or
unchanged, in any number of your own commercial and non-commercial projects: games, apps,
films, renders and similar finished works.

You may not:
- resell, share or give away the models, palettes, patterns, materials, prefabs, layouts,
  scenes, shaders or scripts on their own, changed or unchanged;
- include them in another asset pack, template, marketplace upload or content library;
- use them as training data for machine learning models.

Shipping them inside a finished game or app is fine, as long as they are not offered as
separate source assets. A game in which players furnish rooms with the builder is a finished
work. Credit is not required but appreciated.

The models, palettes, patterns, materials, prefabs, layouts, scenes, shaders and scripts are
provided as is, without warranty of any kind. The author is not liable for any damage arising
from their use.

If you got this pack on the Unity Asset Store, the Unity Asset Store EULA applies instead.
If you got it on itch.io, this text is the licence.
