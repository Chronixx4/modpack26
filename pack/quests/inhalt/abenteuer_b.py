from dsl import gruppe, kapitel, Q

# ---------------------------------------------------------------------------
# Bosse (Gruppe 'abenteuer', wird in abenteuer_a angelegt)
# Hauptlinie: Wroughtnaut -> Elder Guardian -> Wither -> Drache -> Cataclysm
# ---------------------------------------------------------------------------
kapitel('bosse', '&cBosse', 'minecraft:wither_skeleton_skull', 'Von leicht bis brutal: alle großen Gegner im Pack', [
    Q('start', 'Auf zur Jagd!', 'check',
      'In diesem Pack warten jede Menge Bosse. Von links nach rechts werden sie härter.\n\n'
      'Tipp: Gute Rüstung, Essen und ein paar Heiltränke gehören immer ins Gepäck. Mit dem &eBoss-Respawner&r von Cataclysm kannst du manche Bosse erneut rufen.',
      nach=['erste_schritte/eisen'], belohnung='loot1', haupt=True, form='gear', gross=True),

    # --- Hauptlinie ---
    Q('wroughtnaut', 'Ferrous Wroughtnaut', ('kill', 'mowziesmobs:ferrous_wroughtnaut', 1),
      'Der eiserne Ritter von Mowzie\'s Mobs schläft in einer Kammer unter der Erde.\n\n'
      'Von vorne ist er kaum zu verletzen. Weich seinen Schlägen aus und greif ihn von &ehinten&r an.',
      nach=['start'], belohnung='loot1', haupt=True, form='hexagon'),
    Q('elder_guardian', 'Großer Wächter', ('kill', 'minecraft:elder_guardian', 1),
      'In jedem Ozeanmonument leben drei Große Wächter. Sie geben dir Abbaulähmung, bis alle besiegt sind.\n\n'
      'Milch hebt den Effekt kurz auf, Wasseratmung hilft beim Tauchen.',
      nach=['wroughtnaut'], belohnung=2, haupt=True),
    Q('wither', 'Der Wither', ('kill', 'minecraft:wither', 1),
      'Baue ihn aus vier Seelensand und drei Witherskelettschädeln. Am besten tief unter der Erde, denn er sprengt alles weg.\n\n'
      'Er lässt einen &eNetherstern&r fallen.',
      nach=['elder_guardian', 'dimensionen/nether'], belohnung='loot2', haupt=True, form='hexagon', gross=True),
    Q('drache', 'Der Enderdrache', ('kill', 'minecraft:ender_dragon', 1),
      'Zerstöre zuerst die Endkristalle auf den Obsidiansäulen, sonst heilt sich der Drache.\n\n'
      'Danach steht dir das äußere End offen.',
      nach=['wither', 'dimensionen/end'], belohnung='loot3', haupt=True, form='hexagon', gross=True),
    Q('monstrositaet', 'Netherit-Monstrosität', ('kill', 'cataclysm:netherite_monstrosity', 1),
      'Ab hier kommt &cL_Ender\'s Cataclysm&r. Die Monstrosität bewacht die Seelenschmiede im Nether.\n\n'
      'Die Augen von Cataclysm funktionieren wie ein Enderauge: werfen, folgen, Struktur finden. Hier brauchst du das &eAuge der Monstrosität&r.',
      nach=['drache', 'augen'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('ignis', 'Ignis', ('kill', 'cataclysm:ignis', 1),
      'Der brennende Ritter wartet in der Brennenden Arena im Nether. Das &eFlammenauge&r zeigt dir den Weg.\n\n'
      'Er hinterlässt Ignitium, eines der besten Materialien im Pack.',
      nach=['monstrositaet'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('harbinger', 'Der Harbinger', ('kill', 'cataclysm:the_harbinger', 1),
      'Die Wither-Maschine haust in der Alten Fabrik. Folge dem &eMechanischen Auge&r.\n\n'
      'Pass auf seine Laser und Raketen auf und bleib in Bewegung.',
      nach=['ignis'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('leviathan', 'Der Leviathan', ('kill', 'cataclysm:the_leviathan', 1),
      'Tief in der versunkenen Stadt lauert der Leviathan. Das &eAbgrundauge&r führt dich hin.\n\n'
      'Ohne Wasseratmung oder Conduit wird es hier schnell eng.',
      nach=['harbinger'], belohnung='loot4', haupt=True, form='hexagon'),
    Q('ende', 'Bossjäger', 'check',
      'Du hast die härtesten Bosse des Packs besiegt. Respekt!\n\n'
      'Jetzt wartet nur noch das große Finale.',
      nach=['leviathan', 'scylla'], belohnung='loot4', haupt=True, form='star', gross=True),

    # --- Mowzie's Mobs ---
    Q('wrought_beute', 'Axt der tausend Metalle', 'mowziesmobs:wrought_axe',
      'Der Wroughtnaut kann seine riesige Axt oder seinen Helm fallen lassen. Die Axt trifft mit einem breiten Schwung.',
      nach=['wroughtnaut'], belohnung=2, optional=True, form='heart', zeile=1),
    Q('frostmaw', 'Frostmaw', ('kill', 'mowziesmobs:frostmaw', 1),
      'Der Frostmaw schläft in verschneiten Gebieten. Weck ihn nur, wenn du gut vorbereitet bist.\n\n'
      'Er lässt seinen &eEiskristall&r fallen.',
      nach=['wroughtnaut'], belohnung=2, zeile=-1),
    Q('umvuthi', 'Umvuthi', ('kill', 'mowziesmobs:umvuthi', 1),
      'Der Anführer der Umvuthana lebt in einem Hain in der Savanne. Seine Krieger beschützen ihn.\n\n'
      'Zur Belohnung gibt es das &eSol Visage&r.',
      nach=['frostmaw'], belohnung=3, zeile=-1),
    Q('sculptor', 'Der Sculptor', ('kill', 'mowziesmobs:sculptor', 1),
      'Der Sculptor stellt dich auf eine Kletterprüfung. Wer ihn besiegt, bekommt den &eEarthrend Gauntlet&r.',
      nach=['umvuthi'], belohnung='loot2', zeile=-1),

    # --- Born in Chaos ---
    Q('hundefuehrer', 'Leithund', ('kill', 'born_in_chaos_v1:dire_hound_leader', 1),
      'Born in Chaos bringt finstere Gegner mit. Der Anführer der Schreckenshunde ist der Einstieg.\n\n'
      'Er lässt seinen &eFangzahn&r fallen.',
      nach=['start'], belohnung=2, zeile=-3),
    Q('dunkelmetall', 'Dunkles Metall', 'born_in_chaos_v1:dark_metal_ingot',
      'Aus Dunkelmetall-Stücken machst du Barren für Rüstung und Waffen von Born in Chaos. Schau in JEI, wo du sie findest.',
      nach=['hundefuehrer'], belohnung=2, zeile=-4),
    Q('knochenrufer', 'Oberster Knochenrufer', ('kill', 'born_in_chaos_v1:supreme_bonescaller', 1),
      'Der Oberste Knochenrufer ruft ständig Skelette herbei. Schalte ihn schnell aus, bevor es zu viele werden.',
      nach=['hundefuehrer'], belohnung=2, zeile=-3),
    Q('spinnenmutter', 'Spinnenmutter', ('kill', 'born_in_chaos_v1:mother_spider', 1),
      'Eine riesige Spinne mit vielen Kindern. Flächenschaden hilft gegen die Kleinen.',
      nach=['knochenrufer'], belohnung=2, zeile=-3),
    Q('sir_kuerbis', 'Sir Pumpkinhead', ('kill', 'born_in_chaos_v1:sir_pumpkinhead', 1),
      'Ein Kürbisritter auf seinem Höllenross. Hol ihn zuerst vom Pferd.',
      nach=['spinnenmutter'], belohnung=3, zeile=-3),
    Q('lord_kuerbis', 'Lord Pumpkinhead', ('kill', 'born_in_chaos_v1:lord_pumpkinhead', 1),
      'Der Herr der Kürbisse ist deutlich stärker als sein Knappe. Nimm Feuerschutz mit.',
      nach=['sir_kuerbis'], belohnung='loot2', form='hexagon', zeile=-3),
    Q('chaosritter', 'Gefallener Chaosritter', ('kill', 'born_in_chaos_v1:fallen_chaos_knight', 1),
      'Ein schwer gepanzerter Ritter aus Born in Chaos. Schilde und gute Rüstung lohnen sich hier.',
      nach=['lord_kuerbis'], belohnung=3, zeile=-3),

    # --- Illager und Friends&Foes ---
    Q('eismagier', 'Iceologer', ('kill', 'friendsandfoes:iceologer', 1),
      'Ein Illager, der mit Eisbrocken wirft. Er taucht in kalten Gegenden auf.',
      nach=['start'], belohnung=1, zeile=6),
    Q('invoker', 'Der Invoker', ('kill', 'illagerinvasion:invoker', 1),
      'Der Invoker von Illager Invasion ist ein mächtiger Magier. Er beschwört Fangzähne und Helfer und kann sich schützen.',
      nach=['eismagier'], belohnung=3, zeile=6),
    Q('wildfire', 'Wildfire', [('kill', 'friendsandfoes:wildfire', 1), 'friendsandfoes:wildfire_crown_fragment'],
      'Die Wildfire hütet Bastionen im Nether und wird von Lohen begleitet. Sie lässt Fragmente ihrer Krone fallen.\n\n'
      'Aus den Fragmenten baust du die &eLauffeuer-Krone&r.',
      nach=['invoker', 'dimensionen/nether'], belohnung=3, zeile=6),

    # --- Mutant Monsters ---
    Q('mutant_zombie', 'Mutantenzombie', ('kill', 'mutantmonsters:mutant_zombie', 1),
      'Mutant Monsters macht aus normalen Monstern riesige Brocken. Der Mutantenzombie steht nach dem ersten Tod gerne wieder auf.',
      nach=['start'], belohnung=2, zeile=-2),
    Q('mutant_skelett', 'Mutantenskelett', ('kill', 'mutantmonsters:mutant_skeleton', 1),
      'Aus seinen Knochen kannst du eine eigene Rüstung bauen. Schau in JEI nach.',
      nach=['mutant_zombie'], belohnung=2, zeile=-2),
    Q('mutant_creeper', 'Mutantencreeper', ('kill', 'mutantmonsters:mutant_creeper', 1),
      'Er springt dich an und explodiert mit Wucht. Halte Abstand und kämpfe nicht neben deiner Basis.',
      nach=['mutant_skelett'], belohnung=2, zeile=-2),
    Q('mutant_enderman', 'Mutantenenderman', [('kill', 'mutantmonsters:mutant_enderman', 1), 'mutantmonsters:endersoul_hand'],
      'Der Härteste der Mutanten. Er wirft Blöcke und teleportiert sich.\n\n'
      'Er lässt die &eEnderseelenhand&r fallen.',
      nach=['mutant_creeper'], belohnung='loot2', form='hexagon', zeile=-2),

    # --- Warden (optional) ---
    Q('warden', 'Der Wärter', ('kill', 'minecraft:warden', 1),
      'Der Warden aus der Tiefen Dunkelheit ist extrem stark. Meistens ist Schleichen klüger als Kämpfen.\n\n'
      'Wenn du dich traust: Fernkampf und viel Abstand.',
      nach=['elder_guardian'], belohnung='loot2', optional=True, form='heart', zeile=1),

    # --- Aquamirae ---
    Q('aal', 'Der Aal', ('kill', 'aquamirae:eel', 1),
      'Aquamirae bringt das Eislabyrinth in gefrorene Ozeane. Der riesige Aal lauert unter dem Eis.',
      nach=['elder_guardian'], belohnung=2, zeile=4),
    Q('labyrinthmutter', 'Labyrinthmutter', ('kill', 'aquamirae:maze_mother', 1),
      'Die Labyrinthmutter schwimmt durch das Eislabyrinth und greift aus der Tiefe an.',
      nach=['aal'], belohnung=2, zeile=4),
    Q('cornelia', 'Captain Cornelia', ('kill', 'aquamirae:captain_cornelia', 1),
      'Die untote Piratenkapitänin ist der Boss von Aquamirae. Du rufst sie mit dem &eMuschelhorn&r.\n\n'
      'Sie hat starke Ausrüstung dabei, also lohnt sich der Kampf.',
      nach=['labyrinthmutter'], belohnung='loot2', form='hexagon', zeile=4),

    # --- Aether ---
    Q('slider', 'Der Slider', ('kill', 'aether:slider', 1),
      'Im Bronze-Dungeon des Aether wartet der Slider, ein rutschender Steinblock. Er ist nur mit einer Spitzhacke gut zu treffen.\n\n'
      'Sein Schlüssel öffnet die Schatztruhe.',
      nach=['wroughtnaut'], belohnung=2, zeile=5),
    Q('walkuerenkoenigin', 'Walkürenkönigin', ('kill', 'aether:valkyrie_queen', 1),
      'Die Königin der Walküren wohnt im Silber-Dungeon. Sie kämpft erst, wenn du genug &eSiegesmedaillen&r von ihren Walküren hast.',
      nach=['slider'], belohnung=3, zeile=5),
    Q('sonnengeist', 'Sonnengeist', ('kill', 'aether:sun_spirit', 1),
      'Der Sonnengeist ist der letzte Boss des Aether und haust im Gold-Dungeon. Feuerschutz ist Pflicht.',
      nach=['walkuerenkoenigin'], belohnung='loot2', form='hexagon', zeile=5),

    # --- Bosses'Rise ---
    Q('skor', 'Skor, der Yeti', ('kill', 'block_factorys_bosses:yeti', 1),
      'Bosses\'Rise bringt eigene Arenen mit Bossen. Skor ist der Yeti und haust in der Kälte.',
      nach=['drache'], belohnung=2, zeile=-2),
    Q('sirok', 'Sirok, der Sandwurm', ('kill', 'block_factorys_bosses:sandworm', 1),
      'Ein riesiger Wurm, der aus dem Sand bricht. Bleib in Bewegung.',
      nach=['skor'], belohnung=2, zeile=-2),
    Q('piratenkapitaen', 'Piratenkapitän', ('kill', 'block_factorys_bosses:pirate_captain', 1),
      'Der Kapitän der Piraten von Bosses\'Rise hat eine ganze Mannschaft dabei.',
      nach=['sirok'], belohnung=2, zeile=-2),
    Q('nerakyss', 'Nerakyss, der Kraken', ('kill', 'block_factorys_bosses:kraken', 1),
      'Der Kraken greift mit seinen Tentakeln aus dem Wasser an.',
      nach=['piratenkapitaen'], belohnung=3, zeile=-2),
    Q('helvar', 'Helvar', ('kill', 'block_factorys_bosses:underworld_knight', 1),
      'Helvar, der Ritter der Unterwelt, wartet hinter der Arenatür. Dafür brauchst du den &eArenaschlüssel der Unterwelt&r.',
      nach=['nerakyss'], belohnung=3, zeile=-2),
    Q('ashlord', 'Ashlord', ('kill', 'block_factorys_bosses:infernal_dragon', 1),
      'Der höllische Drache ist der stärkste Boss von Bosses\'Rise. Feuerschutz ist Pflicht.',
      nach=['helvar'], belohnung='loot3', form='hexagon', zeile=-2),

    # --- Ice and Fire ---
    Q('zyklop', 'Zyklop', ('kill', 'iceandfire:cyclops', 1),
      'Zyklopen wohnen in Höhlen an der Küste, oft mit einer Schafherde. Er lässt sein &eZyklopenauge&r fallen.',
      nach=['wither'], belohnung=2, zeile=3),
    Q('gorgone', 'Gorgone', [('kill', 'iceandfire:gorgon', 1), 'iceandfire:gorgon_head'],
      'Schau der Gorgone nicht direkt an, sonst wirst du zu Stein. Ihr Kopf versteinert später deine Feinde.',
      nach=['zyklop'], belohnung=3, zeile=3),
    Q('hydra', 'Hydra', ('kill', 'iceandfire:hydra', 1),
      'Die Hydra lebt in Sümpfen. Abgeschlagene Köpfe wachsen nach, also schnell und hart zuschlagen.',
      nach=['gorgone'], belohnung=3, zeile=3),
    Q('feuerdrache', 'Feuerdrache', [('kill', 'iceandfire:fire_dragon', 1), 'iceandfire:fire_dragon_heart'],
      'Feuerdrachen leben in Höhlen oder auf Horsten mit viel Gold. Feuerschutz ist Pflicht.\n\n'
      'Das Herz brauchst du für die Drachenschmiede.',
      nach=['drache'], belohnung='loot3', zeile=2),
    Q('eisdrache', 'Eisdrache', [('kill', 'iceandfire:ice_dragon', 1), 'iceandfire:ice_dragon_heart'],
      'Eisdrachen findest du in verschneiten Gebieten. Ihr Atem friert dich ein.',
      nach=['feuerdrache'], belohnung='loot3', zeile=2),
    Q('blitzdrache', 'Blitzdrache', [('kill', 'iceandfire:lightning_dragon', 1), 'iceandfire:lightning_dragon_heart'],
      'Blitzdrachen leben in warmen, trockenen Gegenden. Sie greifen mit Blitzen an.',
      nach=['eisdrache'], belohnung='loot3', form='hexagon', zeile=2),

    # --- Cataclysm (Seiten) ---
    Q('augen', 'Augen von Cataclysm', [('item', 'cataclysm:monstrous_eye', 1), ('item', 'cataclysm:void_eye', 1)],
      'Die Augen von Cataclysm baust du aus einem Enderauge und passenden Zutaten. Geworfen zeigen sie zur Boss-Struktur.\n\n'
      'Für den Anfang: das &eAuge der Monstrosität&r (Nether) und das &eAuge der Leere&r (End).',
      nach=['drache'], belohnung=2, haupt=True),
    Q('endergolem', 'Endergolem', ('kill', 'cataclysm:ender_golem', 1),
      'Endergolems bewachen die Zerfallene Zitadelle im End. Sie lassen einen &eLeerenkern&r fallen.',
      nach=['augen'], belohnung=2, zeile=-1),
    Q('enderwaechter', 'Enderwächter', ('kill', 'cataclysm:ender_guardian', 1),
      'Der Boss der Zitadelle. Er lässt den &ePanzerhandschuh der Wacht&r fallen.',
      nach=['endergolem'], belohnung='loot3', form='hexagon', zeile=-1),
    Q('ignitium', 'Ignitium', ('item', 'cataclysm:ignitium_ingot', 4),
      'Ignitium ist die Grundlage für Rüstung und Waffen der Flamme. Die Schmiedevorlage baust du aus einer Netheritvorlage.',
      nach=['ignis'], belohnung=3, zeile=1),
    Q('witherit', 'Witherit', 'cataclysm:witherite_ingot',
      'Der Harbinger hinterlässt Witherit. Damit baust du unter anderem den Mechanischen Fusionsamboss.',
      nach=['harbinger'], belohnung=3, zeile=-1),
    Q('altes_relikt', 'Uraltes Relikt', ('kill', 'cataclysm:ancient_remnant', 1),
      'Das Uralte Relikt schläft in der Verfluchten Pyramide in der Wüste. Folge dem &eWüstenauge&r.',
      nach=['ignis'], belohnung=3, zeile=2),
    Q('maledictus', 'Maledictus', [('kill', 'cataclysm:maledictus', 1), 'cataclysm:cursium_ingot'],
      'Im Frostgefängnis wartet der Maledictus. Das &eAuge des Fluchs&r führt dich hin.\n\n'
      'Er lässt &eCursium&r fallen.',
      nach=['altes_relikt'], belohnung='loot3', zeile=2),
    Q('scylla', 'Scylla', [('kill', 'cataclysm:scylla', 1), 'cataclysm:essence_of_the_storm'],
      'Scylla herrscht über die Akropolis. Folge dem &eSturmauge&r.\n\n'
      'Mit der Essenz des Sturms baust du ihre mächtigen Waffen.',
      nach=['maledictus'], belohnung='loot4', form='hexagon', zeile=2),

    Q('goety_apostel', 'Apostel (Goety)', ('kill', 'goety:apostle', 1),
      'Ein Boss aus Goety, den du mit einem Sabbat-Ritual rufst. Wie das geht, steht im Goety-Kapitel.',
      nach=['goety/apostel'], belohnung=3, optional=True, zeile=8),
    Q('goety_wesir', 'Wesir (Goety)', ('kill', 'goety:vizier', 1),
      'Ein Boss aus Goety: Er erscheint, wenn du eine Dunkle Schriftrolle im Waldanwesen benutzt. Mehr im Goety-Kapitel.',
      nach=['goety/wesir'], belohnung=3, optional=True, zeile=9),
])

# ---------------------------------------------------------------------------
# Waffen und Rüstung
# ---------------------------------------------------------------------------
kapitel('waffen', '&4Waffen und Rüstung', 'simplyswords:iron_longsword', 'Schwerter, Bögen und Rüstungen für jede Spielweise', [
    Q('start', 'Waffenkammer', 'check',
      'Kampf ist hier Vanilla, aber du hast viel mehr Waffen zur Auswahl: Simply Swords, Archers, Rogues & Warriors, Paladins & Priests und mehr.\n\n'
      'Jede Waffenart spielt sich anders. Probier einfach aus, was dir liegt.',
      nach=['erste_schritte/eisen'], belohnung='loot1', haupt=True, form='gear', gross=True),

    # --- Hauptlinie: Simply Swords ---
    Q('eisen', 'Eiserne Klinge', 'simplyswords:iron_longsword',
      'Simply Swords hat viele Waffenarten: Langschwert, Katana, Großhammer, Sense, Speer und mehr. Alle gibt es aus Eisen, Gold, Diamant und Netherit.',
      nach=['start'], belohnung=1, haupt=True),
    Q('diamant', 'Diamantklinge', 'simplyswords:diamond_longsword',
      'Die nächste Stufe. Großhammer und Großaxt sind langsam, aber treffen hart. Rapier und Sai sind schnell.',
      nach=['eisen', 'erste_schritte/diamant'], belohnung=2, haupt=True),
    Q('netherit', 'Netheritklinge', 'simplyswords:netherite_longsword',
      'Wie bei Vanilla: Diamantwaffe, Netheritbarren und Netherit-Schmiedevorlage in den Schmiedetisch.',
      nach=['diamant', 'erste_schritte/netherit'], belohnung='loot2', haupt=True, form='hexagon'),
    Q('ignitium', 'Klinge der Flamme', 'simplycataclysm:ignitium_longsword',
      'Simply Swords: Cataclysm wertet Netheritwaffen mit Cataclysm-Material auf. Für Ignitium brauchst du Barren von Ignis und die Ignitium-Schmiedevorlage.',
      nach=['netherit', 'bosse/ignitium'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('ende', 'Waffenmeister', 'check',
      'Beste Waffe, beste Rüstung. Du bist für jeden Boss gerüstet.',
      nach=['ignitium', 'netherit_ruestung'], belohnung='loot3', haupt=True, form='star', gross=True),

    # --- Simply Swords Seitenäste ---
    Q('runentafel', 'Runentafel', 'simplyswords:runic_tablet',
      'Runentafeln findest du in Truhen. Mit Diamant wird daraus eine Runenwaffe mit zufälliger Fähigkeit.',
      nach=['diamant'], belohnung=2, optional=True, form='heart', zeile=-1),
    Q('runenschwert', 'Runenschwert', 'simplyswords:runic_longsword',
      'Jede Runenwaffe bekommt eine zufällige Rune. Wenn sie dir nicht gefällt, bau eine neue.',
      nach=['runentafel'], belohnung=2, optional=True, form='heart', zeile=-1),
    Q('einzigartig', 'Einzigartige Waffe', 'simplyswords:emberblade',
      'Simply Swords hat auch einzigartige Waffen mit Spezialfähigkeiten, die nur in Truhen liegen. Die Glutklinge ist eine davon.',
      nach=['eisen'], belohnung=3, optional=True, form='heart', zeile=-1),

    # --- Archers ---
    Q('archer_werkbank', 'Bogenbauer', 'archers:archers_workbench',
      'Die Werkbank ist der Arbeitsplatz des Bogenmacher-Dorfbewohners. Bögen und Rüstung kannst du aber auch selbst bauen.',
      nach=['start'], belohnung=1, zeile=-2),
    Q('langbogen', 'Kompositbogen', 'archers:composite_longbow',
      'Ein Langbogen aus Knochen. Er zieht langsamer, schießt aber stärker als der normale Bogen.',
      nach=['archer_werkbank'], belohnung=1, zeile=-2),
    Q('archer_ruestung', 'Bogenschützen-Rüstung', 'archers:archer_armor_chest',
      'Leichte Rüstung, die Fernkampfschaden verstärkt. Gibt es als Bogenschützen- und Waldläufer-Set.',
      nach=['langbogen'], belohnung=2, zeile=-2),
    Q('koenigsbogen', 'Königlicher Langbogen', 'archers:royal_longbow',
      'Ein starker Langbogen aus Gold und Diamant. Mit Netherit wird er noch besser.',
      nach=['archer_ruestung'], belohnung=2, zeile=-2),
    Q('deadeye', 'Deadeye-Rüstung', 'archers_expansion:deadeye_chest',
      'Archers Expansion bringt weitere Sets für Fernkämpfer. Für das Deadeye-Set brauchst du &eGehärtetes Leder&r.',
      nach=['archer_ruestung'], belohnung=2, zeile=-3),
    Q('kriegsbogner', 'Kriegsbogner-Rüstung', 'archers_expansion:war_archer_chest',
      'Das schwerere Set für Bogenschützen. Es braucht Netheritschrott.',
      nach=['deadeye'], belohnung=2, zeile=-3),

    # --- Rogues & Warriors ---
    Q('waffenwerkbank', 'Waffenschmied', 'rogues:arms_workbench',
      'Rogues & Warriors bringt Dolche, Doppeläxte, Glefen und Sicheln. Die Werkbank ist der Arbeitsplatz des Waffenschmieds.',
      nach=['start'], belohnung=1, zeile=1),
    Q('dolch', 'Schneller Dolch', 'rogues:iron_dagger',
      'Dolche treffen sehr schnell. Die Doppelaxt ist das genaue Gegenteil: langsam, aber brutal.',
      nach=['waffenwerkbank'], belohnung=1, zeile=1),
    Q('schurke', 'Schurken-Rüstung', 'rogues:rogue_armor_chest',
      'Leichte Rüstung für flinke Kämpfer. Das Assassinen-Set ist die nächste Stufe.',
      nach=['dolch'], belohnung=2, zeile=1),
    Q('krieger', 'Krieger-Rüstung', 'rogues:warrior_armor_chest',
      'Schwere Rüstung für den Nahkampf. Die Berserker-Rüstung baut darauf auf.',
      nach=['dolch'], belohnung=2, zeile=2),
    Q('netherit_ruestung', 'Netherit-Berserker', 'rogues:netherite_berserker_armor_chest',
      'Fast jede Klassen-Rüstung lässt sich mit Netherit aufwerten. Hier am Beispiel der Berserker-Brust.',
      nach=['krieger', 'erste_schritte/netherit'], belohnung='loot2', form='hexagon', zeile=2),

    # --- Immersive Armors ---
    Q('schwere_ruestung', 'Schwere Rüstung', 'immersive_armors:heavy_chestplate',
      'Immersive Armors hat viele Sets mit eigenen Effekten. Die schwere Rüstung schützt stark, macht dich aber langsam.',
      nach=['start'], belohnung=1, zeile=-4),
    Q('wither_ruestung', 'Wither-Rüstung', 'immersive_armors:wither_chestplate',
      'Aus der Knochenrüstung und Netherziegeln wird die Wither-Rüstung.',
      nach=['schwere_ruestung'], belohnung=2, zeile=-4),
    Q('goettlich', 'Göttliche Rüstung', 'immersive_armors:divine_chestplate',
      'Aus der Robe, Gold und einem &eTotem der Unsterblichkeit&r. Sie fängt ab und zu einen Treffer ganz ab.',
      nach=['wither_ruestung'], belohnung=3, zeile=-4),

    # --- Artifacts ---
    Q('mimic', 'Falsche Truhe!', ('kill', 'artifacts:mimic', 1),
      'Manche Truhen in Höhlen und Strukturen sind Mimics. Sie beißen zu, wenn du sie öffnest. Besiegte Mimics lassen Artefakte fallen.',
      nach=['start'], belohnung=2, optional=True, form='heart', zeile=3),
    Q('artefakt', 'Erstes Artefakt', 'artifacts:running_shoes',
      'Artefakte findest du in Truhen und bei Mimics. Sie kommen in die Zusatzplätze und geben dauerhaft Boni, z. B. schnelleres Laufen.',
      nach=['mimic'], belohnung=2, optional=True, form='heart', zeile=3),

    # --- Paladins & Priests ---
    Q('moench', 'Mönchswerkbank', 'paladins:monk_workbench',
      'Paladins & Priests bringt heilige Krieger und Heiler. Die Mönchswerkbank ist der Arbeitsplatz des Mönch-Dorfbewohners. Für sie brauchst du eine Ghast-Träne aus dem Nether.',
      nach=['start', 'dimensionen/nether'], belohnung=1, zeile=5),
    Q('heilrunen', 'Heilrunen', ['runes:crafting_altar', ('item', 'runes:healing_stone', 16)],
      'Die Zauber von Paladinen und Priestern verbrauchen &eHeilrunen&r. Aus Stein und Goldnuggets geht es auch an der Werkbank, der Runenaltar macht aber viel mehr Runen daraus.',
      nach=['moench'], belohnung=1, zeile=4),
    Q('heiligstab', 'Heiliger Stab', 'paladins:holy_staff',
      'Die Waffe der Priester: Mit dem Stab heilst du dich und deine Freunde. Welche Zauber er hat, zeigt der Tooltip.',
      nach=['moench'], belohnung=1, zeile=5),
    Q('prior', 'Prior-Gewand', 'paladins:prior_robe_chest',
      'Die Robe für Heiler, aus Wolle, Gold und Ghast-Tränen. Die Werte siehst du im Tooltip.',
      nach=['heiligstab'], belohnung=2, zeile=5),
    Q('kriegshammer', 'Großhammer', 'paladins:iron_great_hammer',
      'Die Waffe der Paladine: langsam, aber wuchtig, und mit heiligen Zaubern.',
      nach=['moench'], belohnung=1, zeile=6),
    Q('kite_schild', 'Drachenschild', 'paladins:iron_kite_shield',
      'Der große Schild der Paladine. Er gehört in die Zweithand, neben Hammer oder Streitkolben.',
      nach=['kriegshammer'], belohnung=1, zeile=7),
    Q('paladin_ruestung', 'Paladin-Rüstung', 'paladins:paladin_armor_chest',
      'Schwere Rüstung aus Eisen und Kupfer für Paladine.',
      nach=['kriegshammer'], belohnung=2, zeile=6),
    Q('kreuzritter', 'Kreuzritter', 'paladins:crusader_armor_chest',
      'Die bessere Paladin-Rüstung, aus Eisen, Gold und Ghast-Tränen.',
      nach=['paladin_ruestung'], belohnung=2, zeile=6),
    Q('netherit_paladin', 'Netherit-Kreuzritter', 'paladins:netherite_crusader_armor_chest',
      'Wie bei Vanilla: Kreuzritter-Rüstung, Netheritbarren und Netherit-Schmiedevorlage in den Schmiedetisch.',
      nach=['kreuzritter', 'erste_schritte/netherit'], belohnung='loot2', form='hexagon', zeile=6),

    # --- Shield Expansion ---
    Q('schild_eisen', 'Eisenschild', 'shieldexp:iron_shield', [
        'Shield Expansion macht Schilde spannender: Sie haben Ausdauer und nach dem Blocken eine Abklingzeit. Heb den Schild &ekurz vor dem Treffer&r, dann &aparierst&r du den Angriff.',
        'Shift im Tooltip zeigt Parierdauer, Ausdauer und Explosionswiderstand. Es gibt Schilde aus Holz, Gold, Eisen, Diamant und Netherit.',
    ], nach=['start'], belohnung=1, zeile=-5),
    Q('schild_diamant', 'Diamantschild', 'shieldexp:diamond_shield',
      'Mehr Ausdauer, besseres Parieren.',
      nach=['schild_eisen', 'erste_schritte/diamant'], belohnung=2, zeile=-5),
    Q('schild_netherit', 'Netheritschild', 'shieldexp:netherite_shield',
      'Diamantschild, Netheritbarren und Netherit-Schmiedevorlage in den Schmiedetisch. Der beste Schild aus Shield Expansion.',
      nach=['schild_diamant', 'erste_schritte/netherit'], belohnung=2, zeile=-5),
])

# ---------------------------------------------------------------------------
# L2 Hostility: stärkere Mobs, Level und Eigenschaften
# ---------------------------------------------------------------------------
kapitel('hostility', '&cGefahrenstufen', 'l2hostility:hostility_detector', 'L2 Hostility: Monster mit Level und Eigenschaften', [
    Q('start', 'Gefahrenkompass', 'l2hostility:hostility_detector', [
        'Mit L2 Hostility haben Monster ein &eLevel&r: mehr Leben, mehr Schaden und besondere Eigenschaften (Traits). Das Level hängt von Dimension, Biom, Entfernung zum Spawn und deinem eigenen Gefahrenlevel ab.',
        'Wer Monster tötet, steigt im Level, wer stirbt, sinkt. In der Hand zeigt dir der Gefahrenkompass die Werte, alles Weitere steht im Gefahren-Reiter im Inventar.',
    ], nach=['erste_schritte/eisen'], belohnung='loot1', haupt=True, form='gear', gross=True),
    Q('brille', 'Unsichtbares sehen', 'l2hostility:detector_glasses',
      'Manche Monster sind unsichtbar oder machen dich blind. Mit der Detektorbrille siehst du sie trotzdem.',
      nach=['start', 'dimensionen/nether'], belohnung=2, zeile=-1),
    Q('charm', 'Beute-Talisman', 'l2hostility:charm_of_looting_1',
      'Mit dem Talisman lassen Monster mit bestimmten Eigenschaften besondere Beute fallen. Was genau, zeigt dir JEI.',
      nach=['start'], belohnung=2, zeile=1),
    Q('orb', 'Hostility-Kugel', 'l2hostility:hostility_orb',
      'Erreicht dein Gefahrenlevel bestimmte Stufen, bekommst du beim nächsten Kill eine Belohnung, manchmal eine Hostility-Kugel. Sie sorgt dafür, dass in einem Bereich keine Monster mit Level mehr spawnen. Wie man sie benutzt, steht im Tooltip.',
      nach=['start'], belohnung=2, haupt=True),
    Q('vernunft', 'Flasche der Vernunft', 'l2hostility:bottle_of_sanity',
      'Setzt dein eigenes Gefahrenlevel komplett zurück. Gut, wenn es dir zu heftig wird.',
      nach=['orb'], belohnung=2, zeile=-1),
    Q('chaos', 'Chaos-Barren', 'l2hostility:chaos_ingot',
      'Für den Chaos-Barren brauchst du seltsame Zutaten aus L2 Complements, z. B. Unliving Ice oder Seelenflamme. Wie du sie bekommst, steht in ihrem Tooltip, das Rezept in JEI.',
      nach=['orb'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('ende', 'Wunder-Barren', 'l2hostility:miracle_ingot',
      'Chaos-Barren, Hostility-Essenz und Wunderstaub: das stärkste Material von L2 Hostility.',
      nach=['chaos'], belohnung='loot4', haupt=True, form='star', gross=True),
])

# ---------------------------------------------------------------------------
gruppe('endgame', '&cEndgame', 'minecraft:nether_star')
# ---------------------------------------------------------------------------

# Allthemodium
kapitel('allthemodium', '&6Allthemodium', 'allthemodium:allthemodium_ingot', 'Drei Erze, drei Dimensionen, die stärkste Ausrüstung', [
    Q('start', 'Allthemodium', 'allthemodium:raw_allthemodium',
      'Allthemodium ist das erste von drei Endgame-Erzen. Abbauen kannst du es nur mit Netherit-Werkzeug.\n\n'
      'In der Oberwelt findest du es tief unten in &eAntiken Städten&r.',
      nach=['erste_schritte/netherit'], belohnung='loot2', haupt=True, form='gear', gross=True),
    Q('barren', 'Allthemodium-Barren', ('item', 'allthemodium:allthemodium_ingot', 4),
      'Im Ofen wird aus dem rohen Erz ein Barren. Mit Mekanism oder Create holst du mehr aus jedem Erz.',
      nach=['start'], belohnung=2, haupt=True),
    Q('teleporter', 'Teleport-Pad', 'allthemodium:teleport_pad',
      'Aus einer Enderperle und Allthemodium-Nuggets. Je nachdem, wo du das Pad benutzt, bringt es dich in eine andere Dimension.\n\n'
      'In der Oberwelt geht es in die &eMining-Dimension&r.',
      nach=['barren'], belohnung=2, haupt=True),
    Q('mining', 'Mining-Dimension', ('dim', 'allthemodium:mining'),
      'Eine Welt nur zum Graben, ohne Strukturen. Ganz unten liegt Allthemodium-Erz in Massen.',
      nach=['teleporter'], belohnung='loot2', haupt=True, form='hexagon'),
    Q('vorlage', 'Allthemodium-Vorlage', 'allthemodium:allthemodium_upgrade_smithing_template',
      'Die Vorlage wertet Netherit-Ausrüstung im Schmiedetisch auf. Eine Vorlage, Netheritbarren und Tiefenschiefer ergeben zwei neue.',
      nach=['mining'], belohnung=2, haupt=True),
    Q('atm_werkzeug', 'Allthemodium-Spitzhacke', 'allthemodium:allthemodium_pickaxe',
      'Netherit-Spitzhacke, Vorlage und Barren in den Schmiedetisch. Damit baust du auch Vibranium ab.',
      nach=['vorlage'], belohnung=2, haupt=True),
    Q('atm_ruestung', 'Allthemodium-Rüstung', ['allthemodium:allthemodium_helmet', 'allthemodium:allthemodium_chestplate',
                                               'allthemodium:allthemodium_leggings', 'allthemodium:allthemodium_boots'],
      'Das volle Set aus Allthemodium. Auch hier ist Netherit die Grundlage.',
      nach=['vorlage'], belohnung='loot2', zeile=1),
    Q('apfel', 'Allthemodium-Apfel', 'allthemodium:allthemodium_apple',
      'Ein Apfel mit Allthemodium-Nuggets. Ein teurer, aber starker Snack.',
      nach=['barren'], belohnung=1, optional=True, form='heart'),
    Q('the_other', 'The Other', ('dim', 'allthemodium:the_other'),
      'Ein Teleport-Pad im Nether bringt dich nach &eThe Other&r. Dort ist es gefährlich, aber hier liegt Vibranium.',
      nach=['atm_werkzeug', 'dimensionen/nether'], belohnung='loot2', haupt=True, form='hexagon'),
    Q('vibranium', 'Vibranium', ('item', 'allthemodium:vibranium_ingot', 4),
      'Vibranium ist die zweite Stufe. Schmilz das Erz wie gewohnt.',
      nach=['the_other'], belohnung=3, haupt=True),
    Q('piglich', 'Piglich', [('kill', 'allthemodium:piglich', 1), 'allthemodium:piglich_heart'],
      'Ein untoter Piglin-Magier aus Allthemodium. Er lässt sein Herz fallen.',
      nach=['the_other'], belohnung=3, optional=True, form='heart'),
    Q('vib_werkzeug', 'Vibranium-Ausrüstung', ['allthemodium:vibranium_upgrade_smithing_template', 'allthemodium:vibranium_pickaxe'],
      'Die Vibranium-Vorlage baust du aus Allthemodium und Altem Stein. Damit wertest du Allthemodium-Werkzeug auf.',
      nach=['vibranium'], belohnung=3, haupt=True),
    Q('vib_ruestung', 'Vibranium-Rüstung', ['allthemodium:vibranium_helmet', 'allthemodium:vibranium_chestplate',
                                            'allthemodium:vibranium_leggings', 'allthemodium:vibranium_boots'],
      'Mit der Vibranium-Vorlage wertest du deine Allthemodium-Rüstung auf.',
      nach=['vib_werkzeug', 'atm_ruestung'], belohnung='loot3', zeile=1),
    Q('beyond', 'The Beyond', ('dim', 'allthemodium:the_beyond'),
      'Ein Teleport-Pad im End führt nach &eThe Beyond&r. Hier wartet das seltenste Erz: Unobtainium.',
      nach=['vib_werkzeug', 'dimensionen/end'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('unobtainium', 'Unobtainium', ('item', 'allthemodium:unobtainium_ingot', 4),
      'Das stärkste Metall des Packs. Abbauen kannst du es nur mit Vibranium-Werkzeug.',
      nach=['beyond'], belohnung=3, haupt=True),
    Q('unob_werkzeug', 'Unobtainium-Spitzhacke', ['allthemodium:unobtainium_upgrade_smithing_template', 'allthemodium:unobtainium_pickaxe'],
      'Die Unobtainium-Vorlage entsteht aus Vibranium und Endstein. Danach geht es wie bei den Stufen davor.',
      nach=['unobtainium'], belohnung='loot3', haupt=True),
    Q('abgabe_unob', 'Abgabe: Unobtainium', ('item', 'allthemodium:unobtainium_ingot', 16),
      'Hast du zu viel Unobtainium? Gib 16 Barren ab.',
      nach=['unobtainium'], belohnung='loot4', abgabe=True, optional=True),
    Q('ende', 'Unobtainium-Rüstung', ['allthemodium:unobtainium_helmet', 'allthemodium:unobtainium_chestplate',
                                     'allthemodium:unobtainium_leggings', 'allthemodium:unobtainium_boots'],
      'Die stärkste Rüstung im Pack. Jede Stufe baut auf der vorherigen auf: Netherit, Allthemodium, Vibranium, Unobtainium.',
      nach=['unob_werkzeug', 'vib_ruestung'], belohnung='loot4', haupt=True, form='star', gross=True),
])

# Das große Finale
kapitel('finale', '&cDas große Finale', 'minecraft:dragon_egg', 'Ein letztes Ziel für jede große Mod', [
    Q('start', 'Das große Finale', 'check',
      'Der Drache ist besiegt, jetzt beginnt das echte Endgame. Für jede große Mod gibt es hier ein letztes Ziel.\n\n'
      'Jede Quest wird frei, sobald du das passende Kapitel abgeschlossen hast.',
      nach=['bosse/drache'], belohnung='loot3', haupt=True, form='gear', gross=True),

    # Technik (Hauptlinie)
    Q('create', 'Meister der Zahnräder', 'check',
      'Du hast Create bis zum Ende gespielt. Deine Fabrik läuft von allein.',
      nach=['start', 'create_3/ende'], belohnung=3, haupt=True),
    Q('ae2', 'Datenspeicher ohne Ende', 'megacells:cell_component_256m',
      'Eine 256M-Komponente von MEGA Cells. Damit ist Speicherplatz kein Thema mehr.',
      nach=['create', 'ae2_addons/ende'], belohnung=3, haupt=True),
    Q('mekanism', 'Antimaterie', 'mekanism:pellet_antimatter',
      'Ein Antimaterie-Pellet aus dem SPS. Dafür brauchst du sehr viel Polonium und noch mehr Strom.',
      nach=['ae2', 'mekanism/ende'], belohnung='loot3', haupt=True, form='hexagon'),
    Q('immersive', 'Railgun', 'immersiveengineering:railgun',
      'Die Railgun von Immersive Engineering verschießt Stäbe mit Strom. Ein echtes Prunkstück.',
      nach=['mekanism', 'immersive/ende'], belohnung=3, haupt=True),
    Q('powah', 'Nitro-Reaktor', 'powah:reactor_nitro',
      'Die stärkste Reaktor-Stufe von Powah. Mehr Strom geht kaum.',
      nach=['immersive', 'powah_reaktoren/ende'], belohnung=3, haupt=True),
    Q('draconic', 'Chaotischer Kern', 'draconicevolution:chaotic_core',
      'Der Chaotische Kern braucht Fragmente des Chaos-Wächters. Das Endziel von Draconic Evolution.',
      nach=['powah', 'draconic/ende'], belohnung='loot4', haupt=True, form='hexagon'),

    # Magie
    Q('ars', 'Erzmagier', 'ars_nouveau:archmage_spell_book',
      'Das Zauberbuch des Erzmagiers hat Platz für die stärksten Glyphen von Ars Nouveau.',
      nach=['start', 'ars_nouveau/ende'], belohnung=3, zeile=-2),
    Q('irons', 'Legendäre Tinte', 'irons_spellbooks:legendary_ink',
      'Mit legendärer Tinte schreibst du Schriftrollen auf höchster Stufe. Das Ziel jedes Magiers in Iron\'s Spells.',
      nach=['ars', 'irons_spells/ende'], belohnung=3, zeile=-2),
    Q('apotheosis', 'Meister der Verzauberung', 'check',
      'Apotheosis ist geschafft: Verzauberungen über dem Maximum, Edelsteine in jedem Sockel.',
      nach=['irons', 'apotheosis/ende'], belohnung=3, zeile=-2),
    Q('mystical', 'Insanium', 'mysticalagradditions:insanium_ingot',
      'Insanium aus Mystical Agradditions ist die höchste Stufe der Essenzen.',
      nach=['apotheosis', 'mystical_agriculture/ende'], belohnung='loot3', zeile=-2, form='hexagon'),

    # Abenteuer
    Q('bosse', 'Bossjäger', 'check',
      'Alle großen Bosse liegen dir zu Füßen.',
      nach=['start', 'bosse/ende'], belohnung=3, zeile=2),
    Q('twilight', 'Herr des Zwielichts', 'check',
      'Der Zwielichtwald hat keine Geheimnisse mehr vor dir.',
      nach=['bosse', 'twilight/ende'], belohnung=3, zeile=2),
    Q('dimensionen', 'Weltenbummler', 'check',
      'Du hast alle Dimensionen des Packs bereist.',
      nach=['twilight', 'dimensionen/ende'], belohnung=3, zeile=2),
    Q('allthemodium', 'Unobtainium', 'allthemodium:unobtainium_block',
      'Ein ganzer Block aus dem seltensten Metall des Packs.',
      nach=['dimensionen', 'allthemodium/ende'], belohnung='loot3', zeile=2, form='hexagon'),

    Q('abgabe_stern', 'Abgabe: Nethersterne', ('item', 'minecraft:nether_star', 4),
      'Zum Abschluss noch ein kleines Opfer: vier Nethersterne.',
      nach=['start'], belohnung='loot4', abgabe=True, optional=True, zeile=-1),

    # Chronicle-Kern: drei Fragmente, im mechanischen Crafter von Create zusammengesetzt (pack/kubejs)
    Q('frag_technik', 'Technik-Fragment', 'kubejs:technik_fragment',
      'Das erste Stück des &6Chronicle-Kerns&r. Dafür brauchst du Antimaterie, einen erweckten Kern, eine Singularität, eine 256M-Komponente, Nitro-Kristall und einen Präzisionsmechanismus. Das Rezept zeigt dir JEI (Taste R auf dem Item).\n\n&7Werkbank 3x3:&r\nAntimaterie-Pellet | Erweckter Kern | Antimaterie-Pellet\nSingularität | 256M-Komponente | Nitro-Kristall\nAntimaterie-Pellet | Präzisionsmechanismus | Antimaterie-Pellet',
      nach=['draconic'], belohnung=3, haupt=True),
    Q('frag_magie', 'Magie-Fragment', 'kubejs:magie_fragment',
      'Das magische Stück: Insanium, ein Wilden-Tribut, Iesnium, legendäre Tinte, Stellarit und Drachenhaut. Das Rezept zeigt dir JEI (Taste R auf dem Item).\n\n&7Werkbank 3x3:&r\nInsanium-Barren | Wilden-Tribut | Insanium-Barren\nIesnium-Barren | Legendäre Tinte | Stellarit-Stück\nInsanium-Barren | Drachenhaut | Insanium-Barren',
      nach=['mystical'], belohnung=3, zeile=-2),
    Q('frag_abenteuer', 'Abenteuer-Fragment', 'kubejs:abenteuer_fragment',
      'Hierfür musst du überall gewesen sein: Ignitium, Drachenstahl, feuriger Barren, verzaubertes Gravitit, Netherstern, Herz der Tiefe, Unobtainium und reine Dunkelheit. Das Rezept zeigt dir JEI (Taste R auf dem Item).\n\n&7Werkbank 3x3:&r\nIgnitium | Drachenstahl (Feuer) | Feuriger Barren\nVerz. Gravitit | Netherstern | Herz der Tiefe\nUnobtainium | Reine Dunkelheit | Unobtainium',
      nach=['allthemodium'], belohnung=3, zeile=2),
    Q('kern', 'Der Chronicle-Kern', 'kubejs:chronicle_core', [
        'Das Herz dieses Packs. Die drei Fragmente, ein Chaotischer Kern, Nethersterne, Präzisionsmechanismen und robuste Bleche werden im &emechanischen Crafter&r von Create zusammengesetzt (5x5).\n\n&7Muster (T=Technik, M=Magie, A=Abenteuer, S=Robustes Blech, P=Präzisionsmechanismus, N=Netherstern, C=Chaotischer Kern):&r\n_ S P S _\nS T N M S\nP N C N P\nS A N A S\n_ S P S _',
        'Wer den Kern in den Händen hält, hat Chronicle wirklich durchgespielt.',
    ], nach=['frag_technik', 'frag_magie', 'frag_abenteuer'], belohnung='loot4', haupt=True, form='hexagon', gross=True),
    Q('ende', 'Pack geschafft!', 'check',
      'Du hast alles erreicht, was dieses Pack zu bieten hat. Glückwunsch!\n\n'
      'Zeit für eine neue Welt? Oder einfach eine riesige Basis bauen.',
      nach=['kern'], belohnung='loot4', haupt=True, form='star', gross=True),
])
