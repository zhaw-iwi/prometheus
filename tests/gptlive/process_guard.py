"""Bound an owned acceptance process and terminate its descendants on timeout."""
import os
import subprocess

def run_owned(command, env, log, timeout):
    if timeout <= 0:
        raise ValueError('A positive process deadline is required')
    child=subprocess.Popen(command,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=os.name!='nt',
                           creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:
        return child.wait(timeout=timeout)
    except (subprocess.TimeoutExpired,KeyboardInterrupt):
        if os.name=='nt':
            subprocess.run(['taskkill','/PID',str(child.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
                           creationflags=subprocess.CREATE_NO_WINDOW)
        else:
            import signal
            os.killpg(child.pid,signal.SIGKILL)
        child.wait(timeout=15)
        raise
