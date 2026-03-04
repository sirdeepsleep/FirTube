use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring};
use jni::JNIEnv;
use argon2::{
    password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2, Params,
};
use rand::rngs::OsRng;

#[no_mangle]
pub extern "system" fn Java_yt_browser_SecurityActivity_argonHash(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let input_str: String = env.get_string(&input).expect("Couldn't get java string!").into();

    // Настройка параметров: 512MB RAM, 3 итерации, 4 потока (parallelism)
    // 512 * 1024 = 524288 KB
    let params = Params::new(524288, 3, 4, None).expect("Invalid params");
    let argon2 = Argon2::new(argon2::Algorithm::Argon2id, argon2::Version::V0x13, params);
    
    let salt = SaltString::generate(&mut OsRng);
    let password_hash = argon2
        .hash_password(input_str.as_bytes(), &salt)
        .expect("Hash failed")
        .to_string();

    env.new_string(password_hash).expect("Couldn't create java string!").into_raw()
}

#[no_mangle]
pub extern "system" fn Java_yt_browser_SecurityActivity_argonVerify(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
    hash: JString,
) -> jboolean {
    let input_str: String = env.get_string(&input).expect("Couldn't get input").into();
    let hash_str: String = env.get_string(&hash).expect("Couldn't get hash").into();

    let parsed_hash = match PasswordHash::new(&hash_str) {
        Ok(h) => h,
        Err(_) => return 0,
    };

    // При верификации параметры (RAM, итерации) извлекаются автоматически из строки хэша
    let is_valid = Argon2::default()
        .verify_password(input_str.as_bytes(), &parsed_hash)
        .is_ok();

    if is_valid { 1 } else { 0 }
}
