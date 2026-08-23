from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from plyer import tts

class JarvisBaseApp(App):
    def build(self):
        layout = BoxLayout(orientation='vertical', padding=30, spacing=20)
        
        self.status_label = Label(
            text="Jarvis Voice Ready Hai!\nSir, main aapki aawaz sunne ke liye tayyar hoon.",
            font_size='20sp',
            halign='center'
        )
        
        btn = Button(
            text="Speak & Test",
            size_hint=(1, 0.25),
            background_color=(0, 0.7, 1, 1)
        )
        btn.bind(on_press=self.speak_jarvis)
        
        layout.add_widget(self.status_label)
        layout.add_widget(btn)
        
        # App khulte hi Jarvis bolega
        try:
            tts.speak("Sir, Jarvis is online and ready.")
        except Exception:
            pass
            
        return layout

    def speak_jarvis(self, instance):
        self.status_label.text = "Jarvis is Speaking..."
        try:
            tts.speak("Hello Aamir Hussain bhai, I am working perfectly.")
        except Exception:
            self.status_label.text = "TTS Error!"

if __name__ == '__main__':
    JarvisBaseApp().run()
    
