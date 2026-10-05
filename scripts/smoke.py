#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Verify disposable loopback HTTP/MySQL workflows and independent nesting invariants."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8132').rstrip('/');checks=0

def check(ok,message):
    """Count independent assertions without logging credentials. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)
def key():return str(uuid.uuid4())
class Client:
    """Use real session cookies and CSRF tokens. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: unexpected {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,'Wrong error: '+path)
        return value

def record(id,actor=None):return (actor or admin).request(f'/jobs/{id}')
def body(id):return {'requestKey':key(),'version':record(id)['version'],'note':'TEST 核对尺寸、方向和人工实际数量'}
def command(actor,id,action,status=200,code=None,extra=None):
    """Bind every mutation to the current whole-job version. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    return actor.request(f'/jobs/{id}/commands/{action}','POST',{**body(id),**(extra or {})},status,code)
def capture():
    """Capture stable read-only responses for restart and independent restore. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard','/jobs?size=50&sort=oldest']
    jobs=admin.request('/jobs?size=50')['rows'];paths += [f'/jobs/{j["id"]}' for j in jobs]
    paths += [f'/revisions/{r["id"]}' for j in jobs for r in record(j['id'])['revisions']]
    return {p:admin.request(p) for p in paths}
def geometry(d):
    """Independently verify returned cuts, area, quantity, orientation and pair separation. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    p=d['layout'];defs={r['id']:r for r in d['parts']};seen=set();counts={k:0 for k in defs}
    check(p['requested']==sum(r['quantity'] for r in defs.values()),'Requested count mismatch')
    def walk(n):
        check(n['width']>0 and n['height']>0,'Nonpositive rectangle')
        area=n['width']*n['height']
        if n['kind']!='CUT':check(not n['children'],'Leaf has children');return area
        check(n['axis'] in ['V','H'] and n['kerf']==p['kerf'],'Cut metadata mismatch')
        axis=n['axis'];extent=n['width'] if axis=='V' else n['height'];origin=n['x'] if axis=='V' else n['y'];first=n['at']-origin;second=extent-first-n['kerf']
        check(first>0 and second>=0,'Cut outside plate')
        check(len(n['children'])==(2 if second else 1),'Cut child count mismatch')
        for i,c in enumerate(n['children']):
            expected=(n['x']+(0 if i==0 else first+n['kerf']),n['y'],first if i==0 else second,n['height']) if axis=='V' else (n['x'],n['y']+(0 if i==0 else first+n['kerf']),n['width'],first if i==0 else second)
            check((c['x'],c['y'],c['width'],c['height'])==expected,'Child does not span current plate')
        check(sum(walk(c) for c in n['children'])+n['kerf']*(n['height'] if axis=='V' else n['width'])==area,'Tree area not conserved');return area
    for b in p['boards']:
        check(walk(b['tree'])==(p['width']-2*p['margin'])*(p['height']-2*p['margin']),'Usable board area mismatch')
        check(b['partArea']+b['kerfArea']+b['leftoverArea']==walk(b['tree']),'Board area mismatch')
        for a in b['placements']:
            r=defs[a['partId']];identity=(a['partId'],a['piece']);check(identity not in seen,'Repeated piece');seen.add(identity);counts[a['partId']]+=1
            w,h=round(r['width']*10),round(r['height']*10)
            check((a['width'],a['height'])==((h,w) if a['rotated'] else (w,h)),'Dimension changed')
            check(not a['rotated'] or r['rotation'],'Forbidden rotation')
            check(a['x']>=p['margin'] and a['y']>=p['margin'] and a['x']+a['width']<=p['width']-p['margin'] and a['y']+a['height']<=p['height']-p['margin'],'Part out of bounds')
        for i,a in enumerate(b['placements']):
            for c in b['placements'][i+1:]:check(a['x']+a['width']+p['kerf']<=c['x'] or c['x']+c['width']+p['kerf']<=a['x'] or a['y']+a['height']+p['kerf']<=c['y'] or c['y']+c['height']+p['kerf']<=a['y'],'Overlap or missing kerf')
    unplaced={r['partId']:r['quantity'] for r in p['unplaced']}
    for id,r in defs.items():check(counts[id]+unplaced.get(id,0)==r['quantity'],'Lost requested pieces')
    check(p['placed']==len(seen) and p['usedSheets']==len(p['boards']),'Result count mismatch')
    check(p['partArea']+p['kerfArea']+p['leftoverArea']+p['trimArea']==p['sheetArea'],'Total area mismatch')

parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--capture',action='store_true');parser.add_argument('--verify',action='store_true');args=parser.parse_args()
check(BASE.startswith('http://127.0.0.1:') or BASE.startswith('http://localhost:'),'Use an isolated loopback instance')
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    saved=json.loads(STATE.read_text());responses=capture()
    if args.capture:
        saved['responses']=responses;STATE.write_text(json.dumps(saved,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(responses),'result':'PASS'}));raise SystemExit
    for path,expected in saved['responses'].items():check(responses[path]==expected,'Persistence mismatch: '+path)
    for name,username in saved['users'].items():check(Client(username,saved['password']).request('/auth/me')['username']==username,'Missing actor: '+name)
    for r in saved['replays']:
        actor=Client(saved['users'][r['actor']],saved['password']);check(actor.request(r['path'],'POST',r['body'])==r['response'],'Cached response changed')
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(responses),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only on a disposable isolated database.')
if STATE.exists():raise SystemExit('Existing QA state; verify it or use a new disposable database.')
check(admin.request('/jobs')['total']==0,'First-start business data must be empty')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};ids={};jobs=[];replays=[]
roles={r['name']:r['id'] for r in admin.request('/admin/roles')};dep=admin.request('/admin/departments','POST',{'name':'TEST 板材复核 '+suffix})['id'];external=admin.request('/admin/departments','POST',{'name':'TEST 外部 '+suffix})['id']
wide=admin.request('/admin/roles','POST',{'name':'TEST 登记ALL '+suffix,'scope':'ALL','permissions':['job.read','cut.write','dashboard','export']})['id']
for name,role,department,label in [('planner',roles['方案设计'],dep,'排样设计'),('review',roles['独立复核'],dep,'独立复核'),('operator',wide,dep,'实物登记'),('second',roles['方案设计'],dep,'共同编辑'),('viewer',roles['部门查阅'],dep,'部门查阅'),('outside',roles['方案设计'],external,'外部设计')]:
    username='test-'+name+'-'+suffix;users[name]=username
    ids[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+label,'password':password,'roleId':role,'departmentId':department,'enabled':True})['id'];clients[name]=Client(username,password)
planner,review,operator=[clients[n] for n in ['planner','review','operator']]
def draft():
    """Create labelled disposable rectangular requirements, no stock movements. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    b={'requestKey':key(),'reference':'TEST-SHEET-'+key()[:8],'name':'TEST 展示板矩形排样','departmentId':dep,'category':'WOOD','material':'TEST 木质展示板／12mm','width':1200,'height':800,'margin':10,'kerf':3,'maxSheets':4,'instructions':'TEST 按固定纹理方向排样。切割前按实际材料和设备复核，逐项登记实物数量。','reviewerId':ids['review'],'operatorId':ids['operator']}
    j=planner.request('/jobs','POST',b);id=j['id'];jobs.append(id);check(planner.request('/jobs','POST',b)==j,'Creation retry changed')
    for code,w,h,q,rot in [('PANEL-A',400,250,3,False),('PANEL-B',300,200,2,True),('PANEL-C',150,150,4,False)]:
        planner.request('/parts','POST',{'requestKey':key(),'version':record(id)['version'],'jobId':id,'code':code,'name':'TEST '+code,'width':w,'height':h,'quantity':q,'rotation':rot})
    return id

def running(id):
    """Generate, check and approve a real stored layout. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    command(planner,id,'calculate');geometry(record(id));command(planner,id,'submit');b=body(id);path=f'/jobs/{id}/commands/approve'
    response=review.request(path,'POST',b);check(review.request(path,'POST',b)==response,'Approval retry changed');replays.append({'actor':'review','path':path,'body':b,'response':response})
    review.request(path,'POST',{**b,'note':'TEST changed'},409,'REQUEST_KEY_REUSED');check(len(response['approvedHash'])==64,'Plan not sealed');command(planner,id,'start')
def fill(id,scrap=0,not_cut=0):
    """Declare actual categories explicitly, preserving observed exceptions. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    for p in record(id)['parts']:
        b={'requestKey':key(),'version':record(id)['version'],'jobId':id,'partId':p['id'],'good':p['quantity']-scrap-not_cut,'scrap':scrap,'notCut':not_cut,'note':'=TEST 人工核对实物'}
        r=operator.request('/results','POST',b);check(operator.request('/results','POST',b)==r,'Result retry changed')
def seal(id,outcome='FINISHED'):
    command(operator,id,'submit-report',extra={'outcome':outcome});command(review,id,'close');check(len(record(id)['closedHash'])==64,'No closed digest')

j1=draft();running(j1);command(planner,j1,'approve',403,'FORBIDDEN');command(operator,j1,'submit-report',409,'ACKNOWLEDGEMENT_REQUIRED',{'outcome':'FINISHED'})
command(operator,j1,'acknowledge');command(operator,j1,'submit-report',409,'INCOMPLETE_RESULT',{'outcome':'FINISHED'});fill(j1);command(operator,j1,'submit-report',extra={'outcome':'FINISHED'});command(review,j1,'return-result');fill(j1);seal(j1);check(record(j1)['outcome']=='COMPLETED','Normal outcome differs')
j2=draft();running(j2);command(operator,j2,'acknowledge');fill(j2,1,0);seal(j2);check(record(j2)['outcome']=='SHORTFALL','Scrap hidden')
j3=draft();running(j3);command(operator,j3,'acknowledge');fill(j3,0,1);seal(j3,'STOPPED');check(record(j3)['outcome']=='STOPPED','Stopped hidden')
j4=draft();p=record(j4)['parts'][0];planner.request('/parts','POST',{'requestKey':key(),'version':record(j4)['version'],'jobId':j4,'code':'OVERSIZE','name':'TEST 过大矩形','width':4000,'height':2000,'quantity':1,'rotation':False});command(planner,j4,'calculate');geometry(record(j4));check(record(j4)['layout']['status']=='PARTIAL','Impossible piece hidden');command(planner,j4,'submit',409,'UNPLACED_PARTS');command(planner,j4,'cancel')
j5=draft();command(planner,j5,'calculate');rid=record(j5)['activeRevisionId'];command(planner,j5,'submit');command(review,j5,'return-plan');check(record(j5)['layout'] is None,'Returned active plan retained');check(admin.request(f'/revisions/{rid}')['layout']['requested']==9,'Historical plan lost')
j6=draft();running(j6);command(operator,j6,'acknowledge');fill(j6) # GUI-ready actual report.
j7=draft();p=record(j7)['parts'][0];b=body(j7);b['jobId']=j7;r=planner.request(f'/parts/{p["id"]}/delete','POST',b);check(planner.request(f'/parts/{p["id"]}/delete','POST',b)==r,'Deleted-part retry changed')
# Input precision, forbidden rotation by server, strict fields, whole-job versions and access scope.
b={'requestKey':key(),'version':record(j7)['version'],'jobId':j7,'code':'FRACTION','name':'TEST精度','width':12.34,'height':30,'quantity':1,'rotation':False};planner.request('/parts','POST',b,400,'INVALID_DIMENSION');b['width']=12.3;b['quantity']=1.5;planner.request('/parts','POST',b,400,'INVALID_INPUT');b['quantity']=1;b['status']='CLOSED';planner.request('/parts','POST',b,400,'INVALID_INPUT')
for name in ['outside']:
    actor=clients[name];actor.request(f'/jobs/{j1}',status=403,code='OUT_OF_SCOPE');actor.request(f'/jobs/{j1}/report.json',status=403,code='OUT_OF_SCOPE');actor.request(f'/revisions/{record(j1)["activeRevisionId"]}',status=403,code='OUT_OF_SCOPE');check(actor.request('/jobs')['total']==0,'Outside list leak')
operator.request('/admin/users',status=403,code='FORBIDDEN');clients['viewer'].request(f'/jobs/{j5}/commands/calculate','POST',body(j5),403,'FORBIDDEN');planner.request('/jobs?size=51',status=400,code='INVALID_INPUT');planner.request('/jobs?sort=sql',status=400,code='INVALID_INPUT');planner.request('/jobs','POST',{},403,csrf=False)
admin.request(f'/jobs/{j6}/commands/close','POST',body(j6),403,'INDEPENDENT_REVIEW_REQUIRED');command(planner,j6,'cancel',409,'INVALID_STATE');command(planner,j1,'calculate',409,'FROZEN')
# Temporarily revoke review, prove old approval command does not bypass live authorization.
r=next(r for r in admin.request('/admin/roles') if r['id']==roles['独立复核']);original={'name':r['name'],'scope':r['scope'],'permissions':r['permissions']};admin.request('/admin/roles/'+str(r['id']),'PUT',{**original,'permissions':[p for p in r['permissions'] if p!='job.review']})
try:
    replay=replays[0];review.request(replay['path'],'POST',replay['body'],403,'FORBIDDEN')
finally:admin.request('/admin/roles/'+str(r['id']),'PUT',original)
check(review.request(replay['path'],'POST',replay['body'])==replay['response'],'Reauthorized retry differs')
# One expected version commits only once, through two independent sessions.
par=[Client(users['planner'],password),Client(users['planner'],password)];b=body(j5)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:answers=list(pool.map(lambda pair:pair[0].request(f'/jobs/{j5}/commands/calculate','POST',{**b,'requestKey':pair[1]},status=(200,409)),zip(par,[key(),key()])))
check(sum(r.get('code')=='STALE_VERSION' for r in answers)==1,'Concurrent overwrite allowed');geometry(record(j5))
with planner.opener.open(BASE+f'/api/jobs/{j6}/parts.csv') as response:csv=response.read().decode('utf-8-sig')
check("'=TEST" in csv,'CSV formulas unescaped');check('zhuatech' not in csv,'Promotional export text');check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Password hash exposed');check(len(admin.request('/admin/permissions'))==8 and len(admin.request('/admin/menus'))==10,'Catalog mismatch')
x=next(a for a in admin.request('/admin/users') if a['id']==ids['outside']);finite={k:x[k] for k in ['username','displayName','roleId','departmentId','enabled']};admin.request('/admin/users/'+str(x['id']),'PUT',{**finite,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(x['id']),'PUT',finite)
a=next(x for x in admin.request('/admin/users') if x['username']=='admin');finite={k:a[k] for k in ['username','displayName','roleId','departmentId','enabled']};admin.request('/admin/users/'+str(a['id']),'PUT',{**finite,'enabled':False},409,'LAST_ADMIN');admin.request('/admin/departments/'+str(dep),'DELETE',status=409,code='CONFLICT')
STATE.parent.mkdir(exist_ok=True);saved={'password':password,'users':users,'userIds':ids,'departmentId':dep,'jobIds':jobs,'uiJobId':j6,'draftJobId':j7,'replays':replays,'responses':capture()};fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(saved,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'jobs':len(jobs),'result':'PASS'}))
