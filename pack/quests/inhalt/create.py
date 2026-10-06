from dsl import gruppe, kapitel, Q

gruppe('technik', '&6Technik', 'create:cogwheel')

# ---------------------------------------------------------------------------
# Create 1: Grundlagen
# ---------------------------------------------------------------------------
kapitel('create_1', '&6Create 1: Grundlagen', 'create:cogwheel', 'Wellen, Zahnräder, Presse, Mixer und Messing', [
    Q('start', 'Andesitlegierung', ('item', 'create:andesite_alloy', 16),
      'Andesit plus Eisen- oder Zinkklumpen ergibt &eAndesitlegierung&r. Daraus besteht fast alles in Create.\n\n'
      'Später kann der Mixer die Legierung für dich herstellen.',
      nach=['erste_schritte/eisen'], belohnung=['loot1', ('item', 'create:andesite_alloy', 16)],
      form='gear', gross=True, haupt=True),

    # --- Kraftübertragung ---
    Q('welle', 'Wellen', ('item', 'create:shaft', 8),
      'Wellen leiten Drehbewegung weiter, in gerader Linie.\n\n'
      'Alles in Create läuft über Rotation: Quelle, Welle, Maschine.',
      nach=['start'], belohnung=1, haupt=True),
    Q('handkurbel', 'Handkurbel', 'create:hand_crank',
      'Die einfachste Kraftquelle: Rechtsklick gedrückt halten und kurbeln. Für den Anfang reicht das zum Testen.',
      nach=['start'], belohnung=1, zeile=-1),
    Q('wasserrad', 'Wasserrad', 'create:water_wheel',
      'Fließendes Wasser dreht das Rad und damit die Welle. Je mehr Seiten vom Wasser getroffen werden, desto besser.\n\n'
      'Mehrere Wasserräder auf einer Welle addieren ihre Kraft.',
      nach=['welle'], belohnung=2, zeile=-1),
    Q('grosses_wasserrad', 'Großes Wasserrad', 'create:large_water_wheel',
      'Langsamer, aber deutlich stärker als das kleine Rad.',
      nach=['wasserrad'], belohnung=2, optional=True, zeile=-2),
    Q('windmuehle', 'Windmühle', ['create:windmill_bearing', ('item', 'create:white_sail', 8)],
      'Lager setzen, Segel daran bauen und mit Rechtsklick auf das Lager starten. Mehr Segel = mehr Kraft.\n\n'
      'Die Segel müssen am Lager kleben, Segelrahmen halten automatisch zusammen.',
      nach=['welle'], belohnung=2, zeile=-1, spalte=3),
    Q('zahnrad', 'Zahnräder', ['create:cogwheel', 'create:large_cogwheel'],
      'Zahnräder geben Rotation seitlich weiter. Klein auf groß halbiert die Drehzahl, groß auf klein verdoppelt sie.',
      nach=['welle'], belohnung=1, haupt=True),
    Q('riemen', 'Förderband', ('item', 'create:belt_connector', 4),
      'Aus getrocknetem Seetang. Verbindet zwei Wellen und transportiert Items und Mobs.\n\n'
      'Mit Rechtsklick auf die erste und dann auf die zweite Welle legst du das Band.',
      nach=['welle'], belohnung=1, zeile=1),
    Q('gehaeuse', 'Andesitgehäuse', ('item', 'create:andesite_casing', 8),
      'Andesitlegierung auf entrindetes Holz anwenden. Das Gehäuse ist die Grundlage vieler Maschinen.',
      nach=['zahnrad'], belohnung=2, haupt=True, form='hexagon'),
    Q('getriebe', 'Getriebe', ['create:gearbox', 'create:vertical_gearbox'],
      'Leitet Rotation um die Ecke, in alle vier Richtungen.',
      nach=['gehaeuse'], belohnung=1, zeile=-1),
    Q('kupplung', 'Kupplung und Gangschaltung', ['create:clutch', 'create:gearshift'],
      'Mit Redstone steuern: Die Kupplung trennt die Verbindung, die Gangschaltung kehrt die Drehrichtung um.',
      nach=['getriebe', 'erste_schritte/redstone'], belohnung=1, zeile=-1),
    Q('tacho', 'Messgeräte', ['create:speedometer', 'create:stressometer'],
      'Der Tachometer zeigt die Drehzahl, der Stressmesser die Belastung des Netzes.\n\n'
      '&cÜberlastung&r stoppt das ganze Netz. Dann brauchst du mehr Kraftquellen oder weniger Maschinen.',
      nach=['gehaeuse'], belohnung=1, zeile=-2),

    # --- Presse und Bleche ---
    Q('presse', 'Mechanische Presse', 'create:mechanical_press',
      'Presst Barren auf einem Depot oder Förderband zu Blechen. Über einem Becken kann sie Items verdichten.',
      nach=['gehaeuse'], belohnung='loot1', haupt=True, form='hexagon'),
    Q('bleche', 'Bleche', [('item', 'create:iron_sheet', 8), ('item', 'create:copper_sheet', 8),
                           ('item', 'create:golden_sheet', 4)],
      'Bleche brauchst du für viele Rezepte. Ein Förderband unter der Presse macht das vollautomatisch.',
      nach=['presse', 'erste_schritte/kupfer', 'erste_schritte/gold'], belohnung=2, haupt=True),
    Q('brille', 'Ingenieursbrille', 'create:goggles',
      'Mit der Brille siehst du beim Hinschauen Drehzahl, Belastung und Inhalt von Maschinen. Ein Muss!',
      nach=['bleche'], belohnung=2, zeile=-2),
    Q('schluessel', 'Schraubenschlüssel', 'create:wrench',
      'Dreht Blöcke und baut Create-Teile mit Schleichen + Rechtsklick sofort ab.',
      nach=['brille'], belohnung=1, zeile=-2),
    Q('depot', 'Depot', 'create:depot',
      'Eine Ablage für ein Item. Ideal unter der Presse oder für den Mechanischen Arm.',
      nach=['gehaeuse'], belohnung=1, zeile=1),
    Q('werfer', 'Gewichteter Werfer', 'create:weighted_ejector',
      'Wirft Items über eine einstellbare Strecke. Ziel festlegen, indem du mit dem Werfer in der Hand auf den Zielblock klickst.',
      nach=['depot', 'bleche'], belohnung=1, optional=True, zeile=1, spalte=5),

    # --- Becken und Mixer ---
    Q('becken', 'Becken', 'create:basin',
      'Das Becken nimmt Items und Flüssigkeiten auf. Mit der Presse darüber wird verdichtet, mit dem Mixer gemischt.',
      nach=['bleche'], belohnung=1, haupt=True),
    Q('mixer', 'Mechanischer Mixer', 'create:mechanical_mixer',
      'Über einem Becken mischt er Zutaten, etwa Andesitlegierung oder Teig.\n\n'
      'Manche Rezepte brauchen Hitze unter dem Becken.',
      nach=['becken'], belohnung='loot1', haupt=True, form='hexagon'),

    # --- Zerkleinern und Fächer ---
    Q('muehlstein', 'Mahlstein', 'create:millstone',
      'Zermahlt Erze, Kies und mehr. Rohe Erze werden zu zerkleinertem Erz, oft mit Bonus.',
      nach=['gehaeuse'], belohnung=1, zeile=2),
    Q('zerkleinert', 'Zerkleinertes Erz', ('item', 'create:crushed_raw_iron', 8),
      'Zerkleinertes Erz schmilzt zu einem Barren. Gewaschen gibt es zusätzlich Klumpen dazu.',
      nach=['muehlstein'], belohnung=1, zeile=2),
    Q('faecher', 'Ummantelter Lüfter', 'create:encased_fan',
      'Bläst Items und Mobs weg oder saugt sie an. Richtig spannend wird er mit Wasser, Feuer oder Lava davor.',
      nach=['bleche'], belohnung=2, zeile=2),
    Q('waschen', 'Waschen', 'check',
      'Stell Wasser vor einen Lüfter und lass zerkleinertes Erz durch den Luftstrom fallen. Heraus kommen Klumpen und manchmal Nebenprodukte.\n\n'
      'Hak die Quest ab, wenn deine Waschanlage läuft.',
      nach=['faecher'], belohnung=2, zeile=2),
    Q('hitze', 'Räuchern und Schmelzen', 'check',
      'Feuer vor dem Lüfter räuchert (kocht Essen), Lava schmilzt. Seelenfeuer macht noch etwas anderes, schau in EMI.\n\n'
      'Hak die Quest ab, wenn du es ausprobiert hast.',
      nach=['faecher'], belohnung=1, zeile=3),

    # --- Items bewegen ---
    Q('trichter', 'Schleuse und Tunnel', ['create:andesite_funnel', 'create:andesite_tunnel'],
      'Die Schleuse nimmt Items von Bändern und aus Kisten oder legt sie hinein. Der Tunnel verteilt Items auf nebeneinanderliegende Bänder.',
      nach=['riemen'], belohnung=1, zeile=1),
    Q('schacht', 'Schacht', ('item', 'create:chute', 4),
      'Lässt Items senkrecht fallen. Mit einem Lüfter darunter oder darüber geht es auch nach oben.',
      nach=['bleche'], belohnung=1, zeile=-3, spalte=6),

    # --- Werkzeuge an Maschinen ---
    Q('bohrer', 'Mechanischer Bohrer', 'create:mechanical_drill',
      'Baut den Block vor sich ab. Mit einem Bohrer an einer Contraption gräbst du ganze Tunnel.',
      nach=['gehaeuse'], belohnung=1, zeile=3, spalte=5),
    Q('saege', 'Mechanische Säge', 'create:mechanical_saw',
      'Fällt Bäume oder sägt Items auf einem Förderband, zum Beispiel Stämme zu Brettern.',
      nach=['bohrer'], belohnung=1, zeile=3),

    # --- Messing ---
    Q('zink', 'Zink', [('item', 'create:raw_zinc', 8), 'create:zinc_ingot'],
      'Zinkerz findest du vor allem in mittleren Höhen. Zink brauchst du für Messing.',
      nach=['start'], belohnung=1, zeile=1),
    Q('lohenbrenner', 'Lohenbrenner', 'create:blaze_burner',
      'Baue einen leeren Lohenbrenner und fang damit im Nether eine Lohe ein (Rechtsklick). Gefüttert mit Brennstoff heizt er ein Becken.\n\n'
      'Eine Lohe reicht, sie lebt im Brenner weiter.',
      nach=['bleche', 'dimensionen/nether'], belohnung='loot1', form='hexagon', zeile=1),
    Q('messing', 'Messing', ('item', 'create:brass_ingot', 8),
      'Kupfer und Zink im Mixer mischen, mit einem &ebeheizten&r Becken (Lohenbrenner darunter). Heraus kommt Messing.\n\n'
      'Messing öffnet die nächste Stufe von Create.',
      nach=['mixer', 'lohenbrenner'], belohnung='loot2', haupt=True, form='hexagon', gross=True),
    Q('messinggehaeuse', 'Messingrahmen', ('item', 'create:brass_casing', 8),
      'Wie das Andesitgehäuse, nur mit Messing. Für die schlaueren Maschinen.',
      nach=['messing'], belohnung=2, haupt=True),
    Q('rosenquarz', 'Rosenquarz', ['create:rose_quartz', ('item', 'create:polished_rose_quartz', 4)],
      'Netherquarz mit Redstone ergibt Rosenquarz. Mit Schmirgelpapier in der Hand polierst du ihn.',
      nach=['erste_schritte/redstone', 'dimensionen/nether'], belohnung=1, zeile=2, spalte=8),
    Q('roehre', 'Elektronenröhre', ('item', 'create:electron_tube', 4),
      'Polierter Rosenquarz auf einem Eisenblech. Die Röhre steckt in fast allen Messing-Maschinen.',
      nach=['rosenquarz'], belohnung=2, zeile=2),
    Q('deployer', 'Einsatzgerät', 'create:deployer',
      'Der Deployer benutzt das Item in seiner Hand wie ein Spieler: Blöcke setzen, klicken, Items auf andere anwenden.\n\n'
      'Er ist ein Kernteil der Sequenziellen Montage.',
      nach=['messinggehaeuse', 'roehre'], belohnung=2, haupt=True),
    Q('crafter', 'Mechanische Handwerkseinheit', ('item', 'create:mechanical_crafter', 4),
      'Mehrere Einheiten zusammen bilden ein großes Rezeptfeld. Manche Rezepte gehen nur hier.\n\n'
      'Mit dem Schraubenschlüssel stellst du die Laufrichtung der Items ein.',
      nach=['deployer'], belohnung=2, haupt=True),

    # --- Flüssigkeiten ---
    Q('rohre', 'Rohre und Pumpe', [('item', 'create:fluid_pipe', 8), 'create:mechanical_pump'],
      'Rohre leiten Flüssigkeiten, die Pumpe bewegt sie. Die Pumprichtung folgt der Drehrichtung.',
      nach=['bleche'], belohnung=1, zeile=-1),
    Q('tank', 'Flüssigkeitstank', ('item', 'create:fluid_tank', 2),
      'Tanks lassen sich zu großen Speichern stapeln und verbinden.',
      nach=['rohre'], belohnung=1, zeile=-1),
    Q('kupfergehaeuse', 'Kupferrahmen', ('item', 'create:copper_casing', 4),
      'Kupfer auf entrindetes Holz. Grundlage für Ausguss und Abfluss.',
      nach=['tank'], belohnung=1, zeile=-1),
    Q('ausguss', 'Ausguss und Abfluss', ['create:spout', 'create:item_drain'],
      'Der Ausguss füllt Flüssigkeit in Items (Eimer, Flaschen, Rezepte). Der Abfluss leert Items aus.',
      nach=['kupfergehaeuse', 'tank'], belohnung=2, zeile=-1),

    # --- Abgaben ---
    Q('abgabe_legierung', 'Abgabe: Andesitlegierung', ('item', 'create:andesite_alloy', 64),
      'Zeig, dass deine Legierungs-Produktion läuft.',
      nach=['mixer'], abgabe=True, optional=True, belohnung='loot2', zeile=1),
    Q('abgabe_messing', 'Abgabe: Messing', ('item', 'create:brass_ingot', 32),
      'Ein Stapel Messing für die Werkstatt.',
      nach=['messing'], abgabe=True, optional=True, belohnung='loot3', zeile=1),

    Q('ende', 'Mahlwerk', ('item', 'create:crushing_wheel', 2),
      'Zwei Mahlwerkräder nebeneinander, gegenläufig gedreht: Erze werden zerkleinert, mit mehr Bonus als beim Mahlstein.\n\n'
      'Damit sind die Grundlagen geschafft. Weiter geht es mit Dampf, Zügen und Logistik!',
      nach=['crafter'], belohnung='loot3', form='star', gross=True, haupt=True),
])
