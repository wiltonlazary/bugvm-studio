
def decorator(arg1):
    print('Wrapped with', arg1, arg2)
    def wraps(f):
        return f
    return wraps

@decorator('arg1')
def do_things():
    print('Things done')