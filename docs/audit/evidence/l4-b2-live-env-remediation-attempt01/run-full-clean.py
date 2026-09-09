from pathlib import Path
import os, subprocess, json
root=Path(r'E:\Project\nexus-quant-gateaudit')
out=Path(os.environ['TEMP'])/'nq-b2-env-remediation-01a083f7'
keep={'SYSTEMROOT','WINDIR','PATH','PATHEXT','TEMP','TMP','COMSPEC','JAVA_HOME','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','PROGRAMFILES','PROGRAMFILES(X86)','COMMONPROGRAMFILES','HOMEDRIVE','HOMEPATH','OS','PROCESSOR_ARCHITECTURE','NUMBER_OF_PROCESSORS'}
env={k:v for k,v in os.environ.items() if k.upper() in keep}
binding=subprocess.check_output(['docker','port','nq-b2-env-01a083f7','5432/tcp'],text=True).strip()
assert binding.startswith('127.0.0.1:') and binding.split(':')[1].isdigit()
env.update(SPRING_DATASOURCE_URL='jdbc:postgresql://'+binding+'/nq_b2_env_full', SPRING_DATASOURCE_USERNAME='postgres', SPRING_DATASOURCE_PASSWORD='b2-env-full-fixture-not-a-secret')
meta={'command':'mvn -f backend/pom.xml test','profilesActive':'UNSET','nqOverrides':[], 'javaOptions':'UNSET','datasource':env['SPRING_DATASOURCE_URL'], 'password':'nonempty public disposable fixture value; not logged', 'excludedRelevantKeys':sorted(k for k in os.environ if k.upper().startswith(('SPRING_','NQ_','MAVEN_OPTS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'))), 'database':'new owned DB, V48, only prerequisite account seeded; no Order/Trade/Ledger'}
(out/'full-environment.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
with (out/'full-maven.log').open('wb') as log:
 result=subprocess.run([r'C:\Windows\System32\cmd.exe','/d','/c',r'D:\Tool\Maven\apache-maven-3.9.12\bin\mvn.cmd','-f','backend/pom.xml','test'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
print('FULL_MAVEN_EXIT='+str(result.returncode))
raise SystemExit(result.returncode)
