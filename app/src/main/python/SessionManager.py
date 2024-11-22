import os
from os.path import join

import requests
import pickle

class SessionManager:
    def __init__(self, source='session_file'):
        filename = join(os.environ["HOME"], f"chaquopy/sessions/{source}")
        os.makedirs(os.path.dirname(filename), exist_ok=True)
        self.session_file = filename
        self.session = requests.Session()
        self.load_session()

    def load_session(self):
        try:
            with open(self.session_file, 'rb') as f:
                self.session.cookies.update(pickle.load(f))
        except FileNotFoundError:
            pass

    def save_session(self):
        with open(self.session_file, 'wb') as f:
            pickle.dump(self.session.cookies, f)

    def get(self, url, **kwargs):
        response = self.session.get(url, **kwargs)
        self.save_session()
        return response

    def post(self, url, data=None, json=None, **kwargs):
        response = self.session.post(url, data=data, json=json, **kwargs)
        self.save_session()
        return response