from dsl import gruppe, kapitel, Q
gruppe('start', '&eStart', 'minecraft:writable_book')
kapitel('willkommen', '&eWillkommen', 'minecraft:writable_book', 'Test', [
    Q('start', 'Willkommen!', 'check', 'Hallo', belohnung='loot1', haupt=True, form='gear', gross=True),
    Q('a', 'A', 'minecraft:iron_ingot', 'x', nach=['start'], haupt=True),
    Q('b', 'B', 'minecraft:iron_block', 'x', nach=['start']),
    Q('c', 'C', 'minecraft:iron_pickaxe', 'x', nach=['start']),
])
gruppe('technik', '&6Technik', 'create:cogwheel')
kapitel('test2', '&6Test2', 'create:cogwheel', 'Test', [
    Q('x', 'X', 'create:cogwheel', 'x', nach=['willkommen/a'], belohnung=['loot4', ('item', 'minecraft:diamond', 2), 3], haupt=True),
    Q('y', 'Y', ('item', 'minecraft:dirt', 64), 'abgabe', nach=['x'], abgabe=True),
    Q('z', 'Z', ('kill', 'minecraft:zombie', 3), 'k', nach=['x', 'willkommen/b'], eine=True),
])
