import os,subprocess,sys,tempfile,unittest
from pathlib import Path
from process_guard import run_owned

class ProcessGuardTest(unittest.TestCase):
    def test_watchdog_terminates_the_owned_fake_provider_tree(self):
        with tempfile.TemporaryDirectory() as directory:
            script=Path(directory)/'fake.py';pidfile=Path(directory)/'pid.txt'
            script.write_text("import os,subprocess,sys,time\nfrom pathlib import Path\np=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])\nPath(sys.argv[1]).write_text(str(p.pid))\ntime.sleep(60)\n")
            with open(os.devnull,'w') as log:
                with self.assertRaises(subprocess.TimeoutExpired):
                    run_owned([sys.executable,str(script),str(pidfile)],dict(os.environ),log,2)
            pid=int(pidfile.read_text())
            if os.name=='nt':
                import ctypes
                handle=ctypes.windll.kernel32.OpenProcess(0x1000,False,pid)
                if handle:
                    code=ctypes.c_ulong();ctypes.windll.kernel32.GetExitCodeProcess(handle,ctypes.byref(code))
                    ctypes.windll.kernel32.CloseHandle(handle);self.assertNotEqual(259,code.value)
    def test_unbounded_work_is_rejected_and_success_returns_normally(self):
        with open(os.devnull,'w') as log:
            with self.assertRaises(ValueError):run_owned([],dict(os.environ),log,0)
            self.assertEqual(0,run_owned([sys.executable,'-c','pass'],dict(os.environ),log,10))
