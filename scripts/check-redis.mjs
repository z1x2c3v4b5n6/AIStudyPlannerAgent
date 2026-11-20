import net from 'node:net'
const socket=net.createConnection({host:process.env.REDIS_HOST || 'localhost',port:Number(process.env.REDIS_PORT || 6379)})
const password=process.env.REDIS_PASSWORD
let stage=password?'auth':'ping'
let buffer=''
function command(...args){
  socket.write('*'+args.length+'\r\n'+args.map(arg=>'$'+Buffer.byteLength(arg)+'\r\n'+arg+'\r\n').join(''))
}
process.exitCode=1
socket.setTimeout(3000,()=>socket.destroy())
socket.on('error',()=>socket.destroy())
socket.on('connect',()=>password?command('AUTH',password):command('PING'))
socket.on('data',data=>{
  buffer+=data
  if(!buffer.includes('\r\n')) return
  if(stage==='auth' && buffer==='+OK\r\n'){
    stage='ping'
    buffer=''
    command('PING')
    return
  }
  if(stage==='ping' && buffer==='+PONG\r\n'){
    console.log('Redis PING PASS')
    process.exitCode=0
  }
  socket.end()
})
socket.on('close',()=>{if(process.exitCode!==0)console.error('Redis PING failed. Check REDIS_HOST, REDIS_PORT and REDIS_PASSWORD.')})
