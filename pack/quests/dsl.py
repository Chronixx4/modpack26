"""Bausteine fuer den Inhalt des Questbuchs (inhalt/*.py).

gruppe(schluessel, titel, icon)           neue Kapitel-Gruppe (gilt fuer folgende Kapitel)
kapitel(datei, titel, icon, untertitel, quests)
Q(schluessel, titel, aufgaben, text, nach=[...], belohnung=..., **extra)

aufgaben:   'mod:item'                    Item haben (1x)
            ('item', 'mod:item', n)       n Stueck haben
            ('kill', 'mod:mob', n)        Mob toeten
            ('dim', 'mod:dimension')      Dimension betreten
            ('struct', 'mod:struktur')    Struktur betreten
            ('biome', 'mod:biom')         Biom betreten
            'check'                       zum Abhaken
            Liste davon = alle muessen erfuellt sein
nach:       Schluessel im selben Kapitel, oder 'kapitel/schluessel' fuer andere Kapitel
belohnung:  int = XP-Level | ('item', id, n) | 'loot1'..'loot4' (Loot-Kiste) | Liste davon
extra:      form='hexagon'|'gear'|'heart'|'star'|'square'|'diamond'|'pentagon'|'octagon'|'rsquare'
            gross=True       groessere Quest (Meilenstein)
            optional=True    zaehlt nicht fuer den Kapitel-Abschluss
            haupt=True       auf der Hauptlinie (Zeile 0)
            zeile=n          feste Zeile (negativ = oben), sonst automatisch
            spalte=n         feste Spalte, sonst Tiefe im Abhaengigkeitsbaum
            abgabe=True      Items werden abgegeben (verbraucht)
            sub='...'        Untertitel
            eine=True        nur EINE der Abhaengigkeiten noetig (statt alle)
"""

GRUPPEN = []
KAPITEL = []
_gruppe = [None]


def gruppe(key, titel, icon):
    GRUPPEN.append({'key': key, 'titel': titel, 'icon': icon})
    _gruppe[0] = key


def kapitel(datei, titel, icon, untertitel, quests):
    KAPITEL.append({'datei': datei, 'titel': titel, 'icon': icon, 'untertitel': untertitel,
                    'quests': quests, 'gruppe': _gruppe[0]})


def Q(key, titel, aufgaben, text=None, nach=(), belohnung=1, sub=None, **kw):
    q = {'key': key, 'titel': titel, 'aufgaben': aufgaben, 'text': text, 'nach': list(nach),
         'belohnung': belohnung, 'sub': sub}
    q.update(kw)
    return q
