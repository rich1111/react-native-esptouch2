
Pod::Spec.new do |s|
  s.name         = "RNEsptouch"
  s.version      = "1.0.0"
  s.summary      = "RNEsptouch"
  s.description  = <<-DESC
                  RNEsptouch
                   DESC
  s.homepage     = ""
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "author" => "author@domain.cn" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/author/RNEsptouch.git", :tag => "master" }
  s.source_files  = "RNEsptouch/**/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end

  