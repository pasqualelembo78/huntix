p = 'VehiclePoiZone.cs'
s = open(p, encoding='utf-8').read()

# Fix ApplyCameraOverrides
old1 = 'rig.SetIndoorOverride(true, dist, 2.4f, 22f);'
new1 = 'rig.SetIndoorOverride(true, "vehicle-poi", dist, 2.4f, 22f);'
assert old1 in s, 'Apply non trovato'
s = s.replace(old1, new1, 1)

# Fix ClearCameraOverrides
old2 = 'rig.SetIndoorOverride(false);'
new2 = 'rig.SetIndoorOverride(false, "vehicle-poi");'
assert old2 in s, 'Clear non trovato'
s = s.replace(old2, new2, 1)

open(p, 'w', encoding='utf-8').write(s)
print('VehiclePoiZone aggiornato con owner esplicito corretto')