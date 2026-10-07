# index-pflegen.py add <slug>... | rm <pfad-teil>...  -> pflegt pack/modrinth.index.json (env wie build-und-test.ps1)
import os
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
import json,sys,time,urllib.request,urllib.parse,fnmatch
IDX=ROOT+'/pack/modrinth.index.json'
def get(u):
    for _ in range(5):
        try: return json.load(urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'chronicle-builder'}),timeout=60))
        except urllib.error.HTTPError as e:
            if e.code==429: time.sleep(20); continue
            raise
idx=json.load(open(IDX,encoding='utf-8-sig'))
excl=[l.strip() for l in open(ROOT+'/pack/server-ausschluss.txt') if l.strip() and not l.startswith('#')]
have={f['downloads'][0].split('/data/')[1].split('/')[0] for f in idx['files'] if 'cdn.modrinth.com' in f['downloads'][0]}
def add(pid, why=''):
    if pid in have: return
    p=get('https://api.modrinth.com/v2/project/'+pid)
    q=urllib.parse.quote
    vs=get(f"https://api.modrinth.com/v2/project/{pid}/version?game_versions={q(json.dumps(['1.21.1']))}&loaders={q(json.dumps(['neoforge']))}")
    rel=[v for v in vs if v['version_type']=='release'] or vs
    if not rel: print('KEINE VERSION', p['slug']); return
    v=rel[0]; f=[x for x in v['files'] if x['primary']] or v['files']; f=f[0]
    ss=p['server_side']
    srv='unsupported' if ss=='unsupported' or any(fnmatch.fnmatch(f['filename'],m) for m in excl) else ('optional' if ss=='optional' else 'required')
    idx['files'].append({'path':'mods/'+f['filename'],'hashes':{'sha1':f['hashes']['sha1'],'sha512':f['hashes']['sha512']},
        'env':{'client':'required','server':srv},'downloads':[f['url']],'fileSize':f['size']})
    have.add(p['id']); print('+',p['slug'],v['version_number'],'server='+srv,why); time.sleep(0.35)
    for d in v['dependencies']:
        if d['dependency_type']=='required' and d.get('project_id'): add(d['project_id'],'(Abh. von '+p['slug']+')')
cmd=sys.argv[1]
if cmd=='add':
    for s in sys.argv[2:]: add(get('https://api.modrinth.com/v2/project/'+s)['id'])
elif cmd=='rm':
    for t in sys.argv[2:]:
        n=[f for f in idx['files'] if t in f['path']]
        for f in n: print('-',f['path']); idx['files'].remove(f)
json.dump(idx,open(IDX,'w',encoding='utf-8'),indent=2,ensure_ascii=False)
