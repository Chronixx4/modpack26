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

# ---------------------------------------------------------------------------
# Create 2: Fortgeschritten
# ---------------------------------------------------------------------------
kapitel('create_2', '&6Create 2: Fortgeschritten', 'create:precision_mechanism',
        'Dampf, Präzision, Contraptions, Züge und Logistik', [
    Q('start', 'Dampfmaschine', 'create:steam_engine',
      'Jetzt wird es ernst: Die Dampfmaschine liefert richtig viel Kraft.\n\n'
      'Sie kommt an einen Kessel, also einen Flüssigkeitstank mit Wasser und Lohenbrennern darunter.',
      nach=['create_1/messing', 'create_1/tank'], belohnung=['loot2', ('item', 'create:fluid_tank', 4)],
      form='gear', gross=True, haupt=True),

    # --- Dampf ---
    Q('kessel', 'Kessel heizen', 'check',
      'Mehr Lohenbrenner, mehr Wasser und ein größerer Tank heben die Kesselstufe. Die Brille zeigt dir, was fehlt.\n\n'
      'Tipp: Mit Brennstoff per Schleuse oder Mechanischem Arm laufen die Brenner dauerhaft. Hak ab, wenn dein Kessel läuft.',
      nach=['start', 'create_1/lohenbrenner'], belohnung=2, zeile=-1),
    Q('lohenkuchen', 'Lohenkuchen', 'create:blaze_cake',
      'Ein Lohenkuchen bringt den Lohenbrenner kurz auf &eÜberhitzt&r. Manche Mixer-Rezepte brauchen das.\n\n'
      'Die Basis bekommt im Ausguss Lava.',
      nach=['kessel'], belohnung=2, zeile=-1),
    Q('pfeife', 'Dampfpfeife', 'create:steam_whistle',
      'Rein zum Spaß: An den Kessel setzen und mit Redstone tuten lassen. Mehrere übereinander geben tiefere Töne.',
      nach=['start'], belohnung=1, optional=True, form='heart', zeile=-2),

    # --- Präzision ---
    Q('praezision', 'Präzisionsgetriebe', ('item', 'create:precision_mechanism', 4),
      'Das erste Rezept der &eSequenziellen Montage&r: Goldblech auf ein Band, dann im Wechsel Zahnrad, großes Zahnrad und Eisenklumpen per Deployer, mehrmals hintereinander.\n\n'
      'Manchmal kommt Schrott heraus. Mehrere Deployer und eine Presse am Band machen das automatisch.',
      nach=['start', 'create_1/deployer'], belohnung='loot2', haupt=True, form='hexagon', gross=True),
    Q('arm', 'Mechanischer Arm', 'create:mechanical_arm',
      'Nimmt Items von Ein- und Ausgängen und verteilt sie. Ziele wählst du, bevor du ihn setzt (Rechtsklick auf die Blöcke).',
      nach=['praezision'], belohnung=2, zeile=1),
    Q('drehzahl', 'Drehzahlregler', 'create:rotation_speed_controller',
      'Stellt die Drehzahl eines großen Zahnrads stufenlos ein. Praktisch für Maschinen, die schnell laufen sollen.',
      nach=['praezision'], belohnung=2, zeile=2),
    Q('abgabe_praezision', 'Abgabe: Präzisionsgetriebe', ('item', 'create:precision_mechanism', 16),
      'Läuft deine Montagestraße? Dann her damit.',
      nach=['praezision'], abgabe=True, optional=True, belohnung='loot3', zeile=3),

    # --- Züge ---
    Q('robust', 'Robustes Blech', ('item', 'create:sturdy_sheet', 8),
      'Pulverisierter Obsidian wird über mehrere Schritte zu Robustem Blech. Wie genau, zeigt dir EMI.',
      nach=['praezision'], belohnung=2, haupt=True),
    Q('bahnrahmen', 'Zugrahmen', ('item', 'create:railway_casing', 8),
      'Robustes Blech auf einen Messingrahmen. Aus diesem Rahmen baust du alles rund um Züge.',
      nach=['robust'], belohnung=2, haupt=True),
    Q('gleis', 'Gleise', ('item', 'create:track', 32),
      'Gleise können in Kurven und Steigungen verlegt werden. Erst einen Punkt anklicken, dann den Zielpunkt.\n\n'
      'In großen Mengen lohnt sich die Sequenzielle Montage.',
      nach=['bahnrahmen'], belohnung=2, haupt=True),
    Q('signal', 'Zugsignal', ('item', 'create:track_signal', 2),
      'Signale teilen die Strecke in Abschnitte, damit Züge nicht ineinander fahren.',
      nach=['bahnrahmen'], belohnung=1, zeile=-1),
    Q('formsignal', 'Formsignal', 'railways:semaphore',
      'Aus Steam \'n\' Rails: ein klassisches Flügelsignal, das an einem Zugsignal die Stellung anzeigt.',
      nach=['signal'], belohnung=1, optional=True, zeile=-1),
    Q('kuppler', 'Zugkuppler', 'railways:track_coupler',
      'Steam \'n\' Rails: Damit kannst du Züge auf der Strecke automatisch kuppeln und trennen.',
      nach=['signal'], belohnung=1, zeile=-2),
    Q('spurweiten', 'Andere Spurweiten', ['railways:track_create_andesite_wide', 'railways:track_create_andesite_narrow'],
      'Steam \'n\' Rails bringt Schmal- und Breitspur sowie Gleise aus allen Holzarten. Schmalspur braucht eigene Drehgestelle.',
      nach=['gleis'], belohnung=1, zeile=1),
    Q('bahnhof', 'Bahnhof', 'create:track_station',
      'Am Bahnhof baust du den Zug zusammen: Drehgestelle aufs Gleis, Zug daran bauen, am Bahnhof &eZusammenbauen&r.\n\n'
      'Mit Sekundenkleber hältst du die Wagenteile zusammen.',
      nach=['gleis'], belohnung='loot2', haupt=True, form='hexagon'),
    Q('tuerteile', 'Bauteile für Zugtüren', ['trainutilities:transistor', 'trainutilities:processing_unit',
                                             'trainutilities:rubber_seal'],
      'Create Train Utilities bringt echte Schiebetüren. Dafür brauchst du Transistor, Steuereinheit und Gummidichtung.',
      nach=['bahnhof'], belohnung=1, zeile=1),
    Q('zugtuer', 'Zugtür', ['trainutilities:sound_unit', 'trainutilities:prototype_door_sf'],
      'Aus dem Prototyp machst du im Steinschneider die Tür in vielen Varianten, vom ICE bis zur Londoner U-Bahn.',
      nach=['tuerteile'], belohnung=2, optional=True, zeile=1),
    Q('zugsteuerung', 'Zugsteuerung', 'create:controls',
      'Ohne Steuerung fährt kein Zug. Setz dich davor und fahr selbst, oder gib dem Zug einen Fahrplan.',
      nach=['bahnhof'], belohnung=2, haupt=True),
    Q('fahrplan', 'Fahrplan', 'create:schedule',
      'Im Fahrplan legst du Halte und Bedingungen fest. Rechtsklick auf den Lokführer, und der Zug fährt allein.',
      nach=['zugsteuerung'], belohnung=2, haupt=True),
    Q('navigator', 'Navigator', 'createrailwaysnavigator:navigator',
      'Create Railways Navigator: Plant Verbindungen über dein Zugnetz, mit Umstiegen, wie eine Bahn-App.',
      nach=['fahrplan'], belohnung=2, zeile=-1),
    Q('bahnhofsuhr', 'Bahnhofsuhr', 'createrailwaysnavigator:train_station_clock',
      'Eine echte Bahnhofsuhr. Dazu gibt es verbesserte Anzeigetafeln für Abfahrtszeiten.',
      nach=['navigator'], belohnung=1, optional=True, form='heart', zeile=-1),

    # --- Logistik ---
    Q('karton', 'Karton', ('item', 'create:cardboard', 16),
      'Aus Zellstoff gepresst. Karton ist das Material für das Paketsystem von Create 6.',
      nach=['fahrplan'], belohnung=1, haupt=True),
    Q('verpacker', 'Verpacker', 'create:packager',
      'Packt den Inhalt eines Lagers in Pakete und packt Pakete wieder aus. Pakete haben eine Adresse.',
      nach=['karton'], belohnung=2, haupt=True),
    Q('frosch', 'Frosch-Anschluss', ('item', 'create:package_frogport', 2),
      'Der Frosch schnappt Pakete und schickt sie über Kettenförderer zum Ziel. Sehr süß, sehr nützlich.',
      nach=['verpacker'], belohnung=2, zeile=1),
    Q('kette', 'Kettenförderer', ('item', 'create:chain_conveyor', 2),
      'Kettenförderer verbinden Punkte über weite Strecken, auch schräg. Frösche hängen sich daran.',
      nach=['frosch'], belohnung=1, zeile=2),
    Q('bottler', 'Flüssigkeiten verpacken', 'create_factory_logistics:jar_packager',
      'Create Factory Logistics: Füllt Flüssigkeiten in Gläser, damit sie wie Pakete durchs Netz reisen.',
      nach=['lagerlink'], belohnung=2, zeile=-1),
    Q('lagerlink', 'Lagerverbindung', 'create:stock_link',
      'Verbindet ein Lager mit dem Logistiknetz. Alle verbundenen Lager bilden zusammen deinen Bestand.',
      nach=['verpacker'], belohnung=2, haupt=True),
    Q('anfrager', 'Redstone-Anfrager', 'create:redstone_requester',
      'Bestellt bei einem Redstone-Signal eine feste Menge Items an eine Adresse.',
      nach=['lagerlink'], belohnung=1, zeile=-2),
    Q('fluessiganzeige', 'Flüssigkeits-Fabrikanzeige', 'create_factory_logistics:factory_fluid_gauge',
      'Wie die Fabrikanzeige, aber für Flüssigkeiten.',
      nach=['bottler'], belohnung=2, zeile=-1),
    Q('netzlink', 'Netzwerk-Verbindung', 'create_factory_logistics:network_link',
      'Factory Logistics: verknüpft Logistiknetze miteinander. Details stehen im Tooltip.',
      nach=['lagerlink'], belohnung=1, optional=True, zeile=1),
    Q('ticker', 'Lagerticker', 'create:stock_ticker',
      'Zeigt deinen ganzen Bestand und nimmt Bestellungen an. Lass dir alles an einen Verpacker liefern.\n\n'
      'Tipp: Mit einem Sitz und einem Blaze davor wird er zum Verkäufer.',
      nach=['lagerlink'], belohnung='loot2', haupt=True, form='hexagon'),

    # --- Contraptions ---
    Q('lager', 'Mechanisches Lager', 'create:mechanical_bearing',
      'Dreht alle angeklebten Blöcke. Damit beginnen &eContraptions&r: bewegliche Bauten aus Blöcken.',
      nach=['create_1/gehaeuse'], belohnung=1, zeile=-4),
    Q('kleber', 'Sekundenkleber', 'create:super_glue',
      'Mit Kleber verbindest du Blöcke zu einer Contraption. Bereich markieren, fertig.',
      nach=['lager'], belohnung=1, zeile=-4),
    Q('kolben', 'Mechanischer Kolben', ['create:mechanical_piston', ('item', 'create:piston_extension_pole', 8)],
      'Schiebt Blöcke vor und zurück, so weit die Verlängerungsstangen reichen.',
      nach=['kleber'], belohnung=1, zeile=-4),
    Q('portalkran', 'Portalkran', ['create:gantry_carriage', ('item', 'create:gantry_shaft', 8)],
      'Der Wagen fährt an der Portalkranachse entlang. Mehrere Achsen quer gebaut ergeben einen 3D-Kran.',
      nach=['kolben'], belohnung=2, zeile=-4),
    Q('seilrolle', 'Flaschenzug', 'create:rope_pulley',
      'Hebt und senkt Blöcke senkrecht am Seil.',
      nach=['portalkran'], belohnung=1, zeile=-4),
    Q('aufzug', 'Aufzug', ['create:elevator_pulley', ('item', 'create:elevator_contact', 2)],
      'Ein echter Aufzug: Seilrolle, Kabine und Kontakte an jedem Stockwerk. In der Kabine wählst du das Ziel.',
      nach=['seilrolle'], belohnung=2, zeile=-4),
    Q('ernte', 'Erntemaschine und Pflug', ['create:mechanical_harvester', 'create:mechanical_plough'],
      'An einer fahrenden Contraption ernten sie Felder bzw. pflügen den Boden.',
      nach=['lager'], belohnung=1, zeile=-5),

    # --- Messing-Logistik und Redstone ---
    Q('messinglogistik', 'Messing-Schleuse und -Tunnel', ['create:brass_funnel', 'create:brass_tunnel'],
      'Mit Filter: Sie lassen nur bestimmte Items durch. Messingtunnel können außerdem gleichmäßig verteilen.',
      nach=['create_1/messing', 'create_1/roehre'], belohnung=2, zeile=4),
    Q('schlauer_schacht', 'Schlauer Schacht', ['create:smart_chute', 'create:content_observer'],
      'Der Schlaue Schacht filtert Items, der Schlaue Beobachter reagiert auf bestimmte Inhalte.',
      nach=['messinglogistik'], belohnung=1, zeile=4),
    Q('funk', 'Redstone-Verbindung', ('item', 'create:redstone_link', 2),
      'Funk für Redstone: zwei Links mit denselben Items als Frequenz sind verbunden.',
      nach=['schlauer_schacht'], belohnung=1, zeile=4),
    Q('anzeige', 'Anzeige-Link', ['create:display_link', ('item', 'create:display_board', 4)],
      'Liest Daten von einem Block (z. B. Lagerinhalt, Zugzeiten) und zeigt sie auf Tafeln oder Schildern an.',
      nach=['funk'], belohnung=2, zeile=4),
    Q('fernsteuerung', 'Fernsteuerung', 'create:linked_controller',
      'Steuert bis zu sechs Redstone-Verbindungen aus der Hand.',
      nach=['funk'], belohnung=1, optional=True, zeile=3),

    # --- Enchantment Industry ---
    Q('schleifstein', 'Mechanischer Schleifstein', 'create_enchantment_industry:mechanical_grindstone',
      'Create: Enchantment Industry startet hier. Der Schleifstein wird von einer Welle angetrieben.',
      nach=['create_1/welle'], belohnung=1, zeile=6),
    Q('erfahrung', 'Flüssige Erfahrung', 'create_enchantment_industry:experience_bucket',
      'Erfahrung gibt es hier als Flüssigkeit. Welche Wege es gibt, zeigt EMI. Fülle einen Eimer damit.',
      nach=['schleifstein'], belohnung=2, zeile=6),
    Q('verzauberer', 'Lohen-Verzauberer', ['create_dragons_plus:blaze_upgrade_smithing_template',
                                           'create_enchantment_industry:blaze_enchanter'],
      'Ein Lohenbrenner wird zum Verzauberer. Gefüttert mit flüssiger Erfahrung und einer Verzauberungsvorlage verzaubert er Items automatisch.',
      nach=['erfahrung', 'create_1/lohenbrenner', 'erste_schritte/verzaubern'], belohnung='loot2', form='hexagon', zeile=6),
    Q('drucker', 'Drucker', 'create_enchantment_industry:printer',
      'Kopiert Bücher, auch verzauberte, mit Erfahrung als Tinte.',
      nach=['verzauberer', 'create_1/ausguss'], belohnung=2, zeile=6),
    Q('schmied', 'Lohen-Schmied', 'create_enchantment_industry:blaze_forger',
      'Die Amboss-Variante des Lohenbrenners. Was er genau kann, steht in EMI.',
      nach=['verzauberer'], belohnung=2, optional=True, zeile=7),
    Q('maschinen_verzaubern', 'Verzauberte Maschinen', 'check',
      'Create: Enchantable Machinery erlaubt Verzauberungen auf Maschinen wie Bohrer, Säge oder Lüfter, z. B. Effizienz oder Glück. Welche gehen, zeigt das Tooltip.\n\n'
      'Hak ab, wenn du es ausprobiert hast.',
      nach=['verzauberer'], belohnung=2, optional=True, zeile=5),

    Q('ende', 'Fabrikanzeige', 'create:factory_gauge',
      'Die Krönung der Logistik: Die Fabrikanzeige hält Bestände auf einer Zielmenge und bestellt fehlende Zutaten selbst nach.\n\n'
      'Damit baust du vollautomatische Fabriken. Glückwunsch!',
      nach=['ticker'], belohnung='loot3', form='star', gross=True, haupt=True),
])
