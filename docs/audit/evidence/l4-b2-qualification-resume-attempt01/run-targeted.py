from pathlib import Path
import os,subprocess,json,time,sys,xml.etree.ElementTree as ET
root=Path(r'E:\Project\nexus-quant-gateaudit'); out=Path(sys.argv[1])
keep={'SYSTEMROOT','WINDIR','PATH','PATHEXT','TEMP','TMP','COMSPEC','JAVA_HOME','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PROGRAMFILES','PROGRAMFILES(X86)','COMMONPROGRAMFILES','HOMEDRIVE','HOMEPATH','OS','PROCESSOR_ARCHITECTURE','NUMBER_OF_PROCESSORS'}
env={k:v for k,v in os.environ.items() if k.upper() in keep}
name='nq-b2-resume-'+out.name.rsplit('-',1)[-1]
def call(args): return subprocess.check_output(args,text=True).strip()
image='postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94'
cid=call(['docker','run','-d','--pull=never','--name',name,'--label','nq.b2.resume='+name,'--tmpfs','/var/lib/postgresql/data','-p','127.0.0.1::5432','-e','POSTGRES_HOST_AUTH_METHOD=trust',image])
(out/'container.json').write_text(json.dumps({'name':name,'id':cid}),encoding='utf-8')
for i in range(80):
 if subprocess.run(['docker','exec',cid,'pg_isready','-U','postgres'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0: break
 time.sleep(.25)
binding=call(['docker','port',cid,'5432/tcp']); assert binding.startswith('127.0.0.1:')
for db in ['nq_l4_blocker']: call(['docker','exec',cid,'createdb','-U','postgres',db])
url='jdbc:postgresql://'+binding+'/nq_l4_blocker'; pw='review-public-fixture'
env.update(SPRING_DATASOURCE_URL=url,SPRING_DATASOURCE_USERNAME='postgres',SPRING_DATASOURCE_PASSWORD=pw)
meta={'profilesActive':'UNSET','excludedRelevantKeys':sorted(k for k in os.environ if k.upper().startswith(('SPRING_','NQ_','MAVEN_OPTS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'))),'datasource':url,'container':cid,'image':image,'password':'public disposable fixture, omitted','sharedDatabaseUsed':False}
(out/'environment.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
base=[os.environ['COMSPEC'],'/d','/c',r'D:\Tool\Maven\apache-maven-3.9.12\bin\mvn.cmd','-f','backend/pom.xml']
tests='CancelledExecutionCorrectionTest,OrderCommandServiceTest,InMemoryOrderStateMachineTest,OkxRestReconcileServiceTest,JdbcOrderRepositoryTest,JdbcTradeRepositoryTest,B2RealProcessProofTest,B0FixtureSafetyTest,B2TerminalCorrectionPostgresIntegrationTest,L4PlanBlockerPostgresIntegrationTest,ReconciliationCursorPostgresIntegrationTest,TradingChainPostgresIntegrationTest,TradingRestartRecoveryPostgresIntegrationTest'
args=['-pl','nq-app','-am','-Dnq.b2=true','-Dnq.b2.pg=true','-Dnq.l4.blockers.enabled=true','-Dspring.datasource.url='+url,'-Dspring.datasource.username=postgres','-Dspring.datasource.password='+pw,'-Dtest='+tests,'-Dsurefire.failIfNoSpecifiedTests=false','test']
with (out/'targeted.log').open('wb') as log: result=subprocess.run(base+args,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
print('TARGETED_EXIT',result.returncode,flush=True)
(out/'targeted-exit.txt').write_text(str(result.returncode))
# Preserve targeted reports before Full Maven overwrites them.
import shutil
for p in root.glob('backend/*/target/surefire-reports/TEST-*.xml'):
 dst=out/'targeted-reports'/p.parents[2].name/p.name; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(p,dst)
print('QUALIFICATION_TARGETED_COMPLETE',flush=True)
