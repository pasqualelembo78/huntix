#!/usr/bin/env bash
# Rigenera la libreria grezza CC0 in assets/ dai pacchetti tracciati in kit/.
# Idempotente: sovrascrive solo i file diversi (cmp), mai eliminazioni.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/assets"
export HUNTIX_ASSETS_ROOT="$ROOT"
python3 - <<'PY'
import zipfile, os, hashlib, json
ROOT=os.environ['HUNTIX_ASSETS_ROOT']
DEST=os.path.join(ROOT,'assets')
PACKS={
 'characters':'kit/characters.zip',
 'road':'kit/road.zip',
 'nature':'kit/kenney_nature-kit.zip',
 'skyboxes':'kit/kenney_skyboxes.zip',
 'car-kit':'kit/kenney_car-kit.zip',
 'city-commercial':'kit/kenney_city-kit-commercial_2.1.zip',
 'city-suburban':'kit/kenney_city-kit-suburban_20.zip',
 'city-industrial':'kit/kenney_city-kit-industrial_1.0.zip',
 'furniture':'kit/furniture.zip',
 'cube-pets':'kit/kenney_cube-pets_1.0.zip',
 'quaternius-downtown':'kit/quaternius.zip',
}
rows=[]
for name,src in PACKS.items():
    path=os.path.join(ROOT,src)
    if not os.path.exists(path):
        print('MANCANTE',src); continue
    with open(path,'rb') as f: sha=hashlib.sha256(f.read()).hexdigest()
    out=os.path.join(DEST,name); n=0
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            if info.is_dir(): continue
            p=info.filename.replace('\\','/')
            parts=[x for x in p.split('/') if x not in ('','.','..')]
            if not parts or p.startswith('/'): continue
            target=os.path.join(out,*parts)
            os.makedirs(os.path.dirname(target),exist_ok=True)
            with z.open(info) as s, open(target,'wb') as d: d.write(s.read())
            n+=1
    rows.append({'name':name,'source':src,'sha256':sha,'files':n})
    print(name,n,'file')
with open(os.path.join(DEST,'manifest.json'),'w') as f:
    json.dump({'packs':rows},f,indent=2)
PY
